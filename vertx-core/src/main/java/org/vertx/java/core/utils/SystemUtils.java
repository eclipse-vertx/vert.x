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
package org.vertx.java.core.utils;

/**
 * Collection of System utils. 
 * 
 * @author Juergen Donnerstag
 */
public class SystemUtils {

	public static final String getPathSeparator() {
		return System.getProperty("path.separator");
	}

	public static String systemVar(final String propName, final String envName, final String def) {
		String val = null;
		if (StringUtils.isNotEmpty(propName)) {
			val = System.getProperty(propName);
		}
		if (StringUtils.isEmpty(val) && StringUtils.isNotEmpty(envName)) {
			val = System.getenv(envName);
		}
		if (StringUtils.isEmpty(val) ) {
			val = def;
		}
		return val;
	}
}
