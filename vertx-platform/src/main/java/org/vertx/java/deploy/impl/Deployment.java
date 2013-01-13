/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl;

import org.vertx.java.core.utils.Args;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Deployment {
	// Every deployment has a unique name
	public final String name;

	// One module can be associated with a deployment
	// (A module may have dependencies on other modules)
	public final VertxModule module;

	// Number of instances of the same Verticle that are started
	// on (hopefully) different threads
	public final int instances;

	// working directory (see preserve-cwd)
	public final File currentModDir;

	// One holder for each instance
	public final List<VerticleHolder> verticles = new ArrayList<>();

	// Deployment tree
	public final List<String> childDeployments = new ArrayList<>();
	public final String parentDeploymentName;

	/**
	 * Constructor
	 */
	public Deployment(final String name, final VertxModule module, final int instances, final File currentModDir,
			final String parentDeploymentName) {
		this.name = (name != null ? name : createName());
		this.module = Args.notNull(module, "module");
		this.instances = instances;
		this.currentModDir = currentModDir;
		this.parentDeploymentName = parentDeploymentName;
	}

	/**
	 * Extension point:
	 * 
	 * @return
	 */
	protected String createName() {
		return "deployment-" + UUID.randomUUID().toString();
	}

	public final boolean hasParent() {
		return parentDeploymentName != null;
	}
}