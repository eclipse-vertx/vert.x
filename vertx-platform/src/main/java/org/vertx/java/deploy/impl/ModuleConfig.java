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
import org.vertx.java.core.utils.lang.Args;

/**
 * A little utility class combining all "get module config" work and make access more safe.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author Juergen Donnerstag
 */
public class ModuleConfig {

	private static final String CONFIG_FILENAME = "mod.json";
	private static final String CONFIG_MAIN = "main";
	private static final String CONFIG_INCLUDES = "includes";
	private static final String CONFIG_AUTO_REDEPLOY = "auto-redeploy";
	private static final String CONFIG_PRESERVE_CWD = "preserve-cwd";
	private static final String CONFIG_WORKER = "worker";

	public static boolean exists(final File modDir) {
		return new File(modDir, CONFIG_FILENAME).canRead();
	}

	private final JsonObject config;

	/**
	 * Constructor
	 * 
	 * @param vertx
	 */
	public ModuleConfig(final JsonObject config) {
		this.config = Args.notNull(config, "config");
	}

	/**
	 * Load the module config from modDir + mod.json. The content must be JSON
	 * compliant.
	 * 
	 * @param modName
	 * @param modDir
	 * @return
	 */
	public ModuleConfig(final File modDir, final String modName) {
		try {
			config = new JsonObject(new File(modDir, CONFIG_FILENAME));
		} catch (Exception ex) {
			throw new RuntimeException("Failed to load config for module: " + modName + "; dir: " + modDir.getAbsolutePath(),
					ex);
		}
	}

	public final JsonObject json() {
		return config;
	}

	public final String main() {
		return config.getString(CONFIG_MAIN);
	}

	public final boolean worker() {
		return config.getBoolean(CONFIG_WORKER, false);
	}

	public final boolean preserveCwd() {
		return config.getBoolean(CONFIG_PRESERVE_CWD, false);
	}

	public final boolean autoRedeploy() {
		return config.getBoolean(CONFIG_AUTO_REDEPLOY, false);
	}

	public List<String> includes() {
		return getParameterList(config.getString(CONFIG_INCLUDES));
	}

	/**
	 * Get the list of elements from a comma separated list. The elements will be
	 * trimmed, and empty entries are ignored. That is, non element returned will
	 * be empty.
	 * 
	 * @param arg
	 * @return Guaranteed to be non-null
	 */
	private List<String> getParameterList(String arg) {
		if (arg == null) {
			return Collections.emptyList();
		}

		arg = arg.trim();
		if (arg.isEmpty()) {
			return Collections.emptyList();
		}

		List<String> res = null;
		String[] sarr = arg.split(",");
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
}
