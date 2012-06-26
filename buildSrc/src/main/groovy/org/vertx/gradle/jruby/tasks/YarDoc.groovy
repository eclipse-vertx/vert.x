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

package org.vertx.gradle.jruby.tasks

import org.gradle.api.tasks.Exec


/**
 * @author pidster
 *
 */
class YarDoc extends Exec {

	def vertxRbSrc = "src/main/ruby/vertx.rb"
	def rubySrc = "src/main/ruby/**/*.rb"

	def licenseSrc = "$project.rootDir/LICENSE.txt"
	def readmeSrc = "$project.rootDir/README.md"
	def destDir = "$project.buildDir/docs/ruby"

	public YarDoc() {
		group = 'documentation'
		description = 'Build Ruby documentation by parsing Ruby script files'

		inputs.files licenseSrc, vertxRbSrc, readmeSrc
		inputs.dir rubySrc
		outputs.dir destDir

		commandLine = [
			'yardoc',
			'--title', "'vert.x Ruby API'",
			'--readme', readmeSrc,
			'--no-private',
			'--output-dir', destDir, vertxRbSrc, rubySrc,
			'-', licenseSrc]
	}

}