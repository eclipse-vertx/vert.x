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
package org.vertx.java.deploy;

import java.io.File;
import java.net.URL;
import java.util.Map;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;

/**
 * @author pidster
 *
 */
public interface VerticleManager {

	public abstract void block();

	public abstract void unblock();

	public abstract JsonObject getConfig();

	public abstract String getDeploymentName();

	public abstract URL[] getDeploymentURLs();

	public abstract File getDeploymentModDir();

	public abstract Logger getLogger();

	public abstract String deploy(boolean worker, String name,
	        final String main, final JsonObject config, final URL[] urls,
	        int instances, File currentModDir, final Handler<Void> doneHandler);

	public abstract void undeployAll(final Handler<Void> doneHandler);

	public abstract void undeploy(String name, final Handler<Void> doneHandler);

	public abstract Map<String, Integer> listInstances();

}