/*
 * Copyright 2019-2020 the original author or authors.
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
package org.springframework.cloud.stream.app.plugin.generator;

import java.io.File;
import java.util.List;

/**
 * @author Christian Tzolov
 */
public class ProjectGeneratorProperties {

	/**
	 * Common pom versions
	 */
	private AppBom appBom;

	/**
	 * Source, processor and sink application configuration
	 */
	private AppDefinition appDefinition;

	/**
	 * Location where the project source files are written.
	 */
	private File outputFolder = new File("./target/output");

	/**
	 * List of binders to generate applications for.
	 */
	private List<String> binders;

	/**
	 * The location of a project's src/main/resources directory.
	 */
	private File projectResourcesDirectory;

	public File getOutputFolder() {
		return outputFolder;
	}

	public void setOutputFolder(File outputFolder) {
		this.outputFolder = outputFolder;
	}

	public AppBom getAppBom() {
		return appBom;
	}

	public void setAppBom(AppBom appBom) {
		this.appBom = appBom;
	}

	public AppDefinition getAppDefinition() {
		return appDefinition;
	}

	public void setAppDefinition(AppDefinition appDefinition) {
		this.appDefinition = appDefinition;
	}

	public List<String> getBinders() {
		return binders;
	}

	public void setBinders(List<String> binders) {
		this.binders = binders;
	}

	public File getProjectResourcesDirectory() {
		return projectResourcesDirectory;
	}

	public void setProjectResourcesDirectory(File projectResourcesDirectory) {
		this.projectResourcesDirectory = projectResourcesDirectory;
	}
}
