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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.utils.Args;

/**
 * A little utility class combining all "get module config" work and make access
 * more safe.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class ModuleConfig {

	public static final String CONFIG_FILENAME = "mod.json";
	private static final String CONFIG_MAIN = "main";
	private static final String CONFIG_INCLUDES = "includes";
	private static final String CONFIG_AUTO_REDEPLOY = "auto-redeploy";
	private static final String CONFIG_PRESERVE_CWD = "preserve-cwd";
	private static final String CONFIG_WORKER = "worker";

	private final JsonObject config;
	private final File modDir;

	public static final boolean exists(final File modDir, final String modName) {
		return exists(modDir, modName, CONFIG_FILENAME);
	}

	public static final boolean exists(final File modDir, final String modName, final String configFile) {
		return getFile(modDir, modName, configFile).canRead();
	}

	public static final File getFile(final File modDir, final String modName) {
		return getFile(modDir, modName, CONFIG_FILENAME);
	}

	public static final File getFile(final File modDir, final String modName, final String configFile) {
		Args.notNull(modDir, "modDir");
		Args.notNull(modName, "modName");
		Args.notNull(configFile, "configFile");

		return new File(modDir, modName + "/" + configFile);
	}

	/**
	 * Constructor
	 */
	public ModuleConfig() {
		this(new JsonObject());
	}

	/**
	 * Constructor
	 */
	public ModuleConfig(final JsonObject config) {
		this.config = (config == null ? new JsonObject() : config);
		this.modDir = null;
	}

	/**
	 * Load the module config from modDir + mod.json. The content must be JSON
	 * compliant.
	 * 
	 * @param modName
	 * @param modDir
	 * @return
	 */
	public ModuleConfig(final File modDir, final String modName) throws Exception {
		this(new File(modDir, modName));
	}

	/**
	 * Load the module config from modDir + mod.json. The content must be JSON
	 * compliant.
	 * 
	 * @param modName
	 * @param modDir
	 * @return
	 */
	public ModuleConfig(final File modDir) throws Exception {
		this.modDir = Args.notNull(modDir, "modDir");
		try {
			config = new JsonObject(configFile());
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load config for module '" + modName() + "' from "
					+ modDir.getAbsolutePath(), ex);
		}
	}

	public final File configFile() {
		return this.modDir == null ? null : new File(this.modDir, CONFIG_FILENAME);
	}

	public final String configFile2() {
		return this.modDir == null ? null : new File(this.modDir, CONFIG_FILENAME).getAbsolutePath();
	}

	public final JsonObject json() {
		return config;
	}

	public final File modDir() {
		return modDir;
	}

	public final String modName() {
		if (modDir == null) {
			return null;
		}
		return modDir.getName();
	}

	public final String main() {
		return config.getString(CONFIG_MAIN);
	}

	public final void main(String main) {
		config.putString(CONFIG_MAIN, main);
	}

	public final boolean worker() {
		return config.getBoolean(CONFIG_WORKER, false);
	}

	public final void worker(boolean worker) {
		config.putBoolean(CONFIG_WORKER, worker);
	}

	public final boolean preserveCwd() {
		return config.getBoolean(CONFIG_PRESERVE_CWD, false);
	}

	public final void preserveCwd(boolean preserveCwd) {
		config.putBoolean(CONFIG_PRESERVE_CWD, preserveCwd);
	}

	public final boolean autoRedeploy() {
		return config.getBoolean(CONFIG_AUTO_REDEPLOY, false);
	}

	public final void autoRedeploy(boolean autoRedeploy) {
		config.putBoolean(CONFIG_AUTO_REDEPLOY, autoRedeploy);
	}

	public List<String> includes() {
		return getParameterList(config.getString(CONFIG_INCLUDES));
	}

	public final void includes(String includes) {
		config.putString(CONFIG_INCLUDES, includes);
	}

	/**
	 * Get the list of elements from a comma separated list. The elements will be
	 * trimmed, and empty entries are ignored. That is, non element returned will
	 * be empty.
	 * 
	 * @param arg
	 * @return Guaranteed to be non-null
	 */
	public static List<String> getParameterList(final String arg) {
		return getParameterList(arg, ",");
	}

	/**
	 * Get the list of elements from a comma separated list. The elements will be
	 * trimmed, and empty entries are ignored. That is, non element returned will
	 * be empty.
	 * 
	 * @param arg
	 * @return Guaranteed to be non-null
	 */
	public static List<String> getParameterList(String arg, final String separator) {
		if (arg == null) {
			return Collections.emptyList();
		}

		arg = arg.trim();
		if (arg.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> res = null;
		String[] sarr = arg.split(separator);
		for (String entry : sarr) {
			entry = entry.trim();
			if (entry.isEmpty() == false) {
				if (res == null) {
					res = new ArrayList<>();
				}
				res.add(entry);
			}
		}

		if (res == null) {
			res = Collections.emptyList();
		}
		return res;
	}
	
	@Override
	public String toString() {
		return config.toString() + "; dir: " + modDir;
	}
}
