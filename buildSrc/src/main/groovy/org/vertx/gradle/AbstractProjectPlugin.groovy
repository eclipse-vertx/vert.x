/*
 * Copyright 2012 the original author or authors.
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
package org.vertx.gradle

import org.gradle.api.Project
import org.gradle.api.Plugin
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin


/**
 * @author pidster
 *
 */
abstract class AbstractProjectPlugin implements Plugin<Project> {
	
	public static final String COMPILE_CONFIGURATION = 'compile'

	public String configurationName
	
	protected AbstractProjectPlugin(String configurationName) {
		this.configurationName = configurationName
	}

	void apply(Project project) {
		project.plugins.apply(JavaPlugin)
		
		Configuration config = project.configurations
			.add(configurationName)
			.setVisible(false)
			.setTransitive(true)
			.setDescription("The $configurationName tools libraries to be used for this $configurationName project.")
		
		Configuration compile = project.configurations.findByName(COMPILE_CONFIGURATION)
		compile.extendsFrom config
		
		configureCompiler(project, config)
		configureDocs(project, config)
	}

	protected abstract void configureCompiler(Project project, Configuration configuration)

	protected abstract void configureResources(Project project, Configuration configuration)

	protected abstract void configureDocs(Project project, Configuration configuration)
	
	
}
