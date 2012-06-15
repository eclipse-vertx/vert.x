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

// TODO Move in cli package
// package org.vertx.java.deploy.impl.cli;
package org.vertx.java.deploy.impl;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses args of the form -x y. All other args are ignored.
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Args {

	private final Map<String, String> map = new HashMap<>();

	/**
	 * Constructor
	 * 
	 * @param args
	 */
	public Args(final String[] args) {
		if (args == null) {
			return;
		}

		String currentKey = null;
		for (String arg : args) {
			if (arg.startsWith("-")) {
				if (currentKey != null) {
					map.put(currentKey, "");
					currentKey = null;
				}
				currentKey = arg;
			} else if (currentKey != null) {
				map.put(currentKey, arg);
				currentKey = null;
			}
		}

		if (currentKey != null) {
			map.put(currentKey, "");
		}
	}

	/**
	 * Gets a String arg. If not present return defaultVal.
	 * 
	 * @param argName
	 * @param defaultVal
	 * @return
	 */
	public String get(String argName, String defaultVal) {
		String val = map.get(argName);
		return (val == null ? defaultVal : val);
	}

	/**
	 * Gets a String arg, If not present return null.
	 * 
	 * @param argName
	 * @return
	 */
	public String get(String argName) {
		return get(argName, null);
	}

	/**
	 * Get arg and convert to int. If not present, return defaultVal.
	 * 
	 * @param argName
	 * @param defaultVal
	 * @return
	 */
	public int getInt(final String argName, final int defaultVal) {
		String arg = map.get(argName);
		if (arg != null) {
			try {
				return Integer.parseInt(arg.trim());
			}
			catch (NumberFormatException e) {
				throw new IllegalArgumentException("Invalid argument: " + argName
						+ ": " + arg + ". Expected an Integer");
			}
		}
		return defaultVal;
	}

	/**
	 * Get arg and convert to int. If not present, return -1.
	 * 
	 * @param argName
	 * @return
	 */
	public int getInt(String argName) {
		return getInt(argName, -1);
	}

	/**
	 * Validate if arg is available
	 * 
	 * @param argName
	 * @return true, if available.
	 */
	public boolean hasOption(String argName) {
		return map.get(argName) != null;
	}
}
