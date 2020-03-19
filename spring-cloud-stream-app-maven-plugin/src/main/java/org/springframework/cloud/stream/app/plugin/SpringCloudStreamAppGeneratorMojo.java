/*
 * Copyright 2020-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.cloud.stream.app.plugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import org.springframework.cloud.stream.app.plugin.generator.AppBom;
import org.springframework.cloud.stream.app.plugin.generator.AppDefinition;
import org.springframework.cloud.stream.app.plugin.generator.ProjectGenerator;
import org.springframework.cloud.stream.app.plugin.generator.ProjectGeneratorProperties;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * @author Christian Tzolov
 */
@Mojo(name = "generate-app")
public class SpringCloudStreamAppGeneratorMojo extends AbstractMojo {

	private static final String WHITELIST_FILE_NAME = "dataflow-configuration-metadata-whitelist.properties";
	private static final String CONFIGURATION_PROPERTIES_CLASSES = "configuration-properties.classes";
	private static final String CONFIGURATION_PROPERTIES_NAMES = "configuration-properties.names";


	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private MavenProject project;

	@Parameter(defaultValue = "${project.resources[0].directory}", readonly = true, required = true)
	private File projectResourcesDir;

	@Parameter(defaultValue = "Docker", required = true)
	private AppDefinition.ContainerImageFormat containerImageFormat;

	@Parameter(defaultValue = "springcloudstream")
	private String containerImageOrgName;

	@Parameter(defaultValue = "false")
	private boolean enableContainerImageMetadata;

	@Parameter(defaultValue = "./apps", required = true)
	private String generatedProjectHome;

	@Parameter(required = true)
	private String generatedProjectVersion; // "3.0.0.BUILD-SNAPSHOT"

	@Parameter(required = true)
	private String generatedAppName;

	@Parameter(required = true)
	private AppDefinition.AppType generatedAppType;

	@Parameter(required = true)
	String configClass;

	@Parameter
	List<String> additionalAppProperties;

	@Parameter
	List<String> metadataSourceTypeFilters = new ArrayList<>();

	@Parameter
	List<String> metadataNameFilters = new ArrayList<>();

	@Parameter
	List<Dependency> boms = new ArrayList<>();

	@Parameter
	List<Dependency> dependencies = new ArrayList<>();

	@Parameter
	List<Dependency> globalDependencies = new ArrayList<>();

	@Parameter
	List<Plugin> additionalPlugins = new ArrayList<>();

	@Parameter
	List<String> binders = new ArrayList<>();

	// Versions
	@Parameter(defaultValue = "2.2.4.RELEASE", required = true)
	private String bootVersion;

	@Parameter(defaultValue = "${app-metadata-maven-plugin-version}")
	private String appMetadataMavenPluginVersion;

	@Override
	public void execute() throws MojoFailureException {
		// Bom
		AppBom appBom = new AppBom()
				.withSpringBootVersion(this.bootVersion)
				.withAppMetadataMavenPluginVersion(this.appMetadataMavenPluginVersion);

		AppDefinition app = new AppDefinition();
		app.setName(this.generatedAppName);
		app.setType(this.generatedAppType);
		app.setVersion(this.generatedProjectVersion);
		app.setFunctionClass(this.configClass);

		this.populateWhitelistFromFile(this.metadataSourceTypeFilters, this.metadataNameFilters);

		if (!CollectionUtils.isEmpty(this.metadataSourceTypeFilters)) {
			app.setMetadataSourceTypeFilters(this.metadataSourceTypeFilters);
		}

		if (!CollectionUtils.isEmpty(this.metadataNameFilters)) {
			app.setMetadataNameFilters(this.metadataNameFilters);
		}

		app.setAdditionalProperties(this.additionalAppProperties);

		// BOM
		app.setMavenManagedDependencies(this.boms.stream()
				.filter(Objects::nonNull)
				.map(dependency -> {
					dependency.setScope("import");
					dependency.setType("pom");
					return dependency;
				})
				.map(MavenXmlWriter::toXml)
				.map(xml -> MavenXmlWriter.indent(xml, 12))
				.collect(Collectors.toList()));

		// Dependencies
		List<Dependency> allDependenciesMerged = new ArrayList<>(this.dependencies);
		allDependenciesMerged.addAll(this.globalDependencies);
		app.setMavenDependencies(allDependenciesMerged.stream()
				.map(MavenXmlWriter::toXml)
				.map(xml -> MavenXmlWriter.indent(xml, 8))
				.collect(Collectors.toList()));

		// Plugins
		app.setMavenPlugins(this.additionalPlugins.stream()
				.map(MavenXmlWriter::toXml)
				.map(d -> MavenXmlWriter.indent(d, 12))
				.collect(Collectors.toList()));

		// Container Image configuration
		app.setContainerImageFormat(this.containerImageFormat);
		app.setEnableContainerImageMetadata(this.enableContainerImageMetadata);
		if (StringUtils.hasText(this.containerImageOrgName)) {
			app.setContainerImageOrgName(this.containerImageOrgName);
		}

		app.setContainerImageTag(this.generatedProjectVersion);

		// Generator Properties
		ProjectGeneratorProperties generatorProperties = new ProjectGeneratorProperties();
		generatorProperties.setBinders(this.binders);
		generatorProperties.setOutputFolder(new File(this.generatedProjectHome));
		generatorProperties.setAppBom(appBom);
		generatorProperties.setAppDefinition(app);
		generatorProperties.setProjectResourcesDirectory(this.projectResourcesDir);

		try {
			new ProjectGenerator().generate(generatorProperties);
		}
		catch (IOException e) {
			throw new MojoFailureException("Project generation failure");
		}
	}

	/**
	 * If the META-INF/dataflow-configuration-metadata-whitelist.properties is provided in the source project, add
	 * its type and name filters to the existing withe-list configurations.
	 * @param sourceTypeFilters existing source type filters configured via the mojo parameter.
	 * @param nameFilters existing name filters configured via the mojo parameter.
	 */
	private void populateWhitelistFromFile(List<String> sourceTypeFilters, List<String> nameFilters) {
		if (this.projectResourcesDir == null || !projectResourcesDir.exists()) {
			return;
		}
		File whitelistPropertiesFile = FileUtils.getFile(projectResourcesDir, "META-INF", WHITELIST_FILE_NAME);
		if (whitelistPropertiesFile.exists()) {
			Properties properties = new Properties();
			try (InputStream is = new FileInputStream(whitelistPropertiesFile)) {
				properties.load(is);
				addToFilters(properties.getProperty(CONFIGURATION_PROPERTIES_CLASSES), sourceTypeFilters);
				addToFilters(properties.getProperty(CONFIGURATION_PROPERTIES_NAMES), nameFilters);
			}
			catch (Exception e) {
				//best effort
			}
		}
	}

	private void addToFilters(String csvFilterProperties, List<String> filterList) {
		if (!StringUtils.isEmpty(csvFilterProperties)) {
			for (String filterProperty : csvFilterProperties.trim().split(",")) {
				if (StringUtils.hasText(filterProperty)) {
					if (!filterList.contains(filterProperty.trim())) {
						filterList.add(filterProperty.trim());
					}
				}
			}
		}
	}
}
