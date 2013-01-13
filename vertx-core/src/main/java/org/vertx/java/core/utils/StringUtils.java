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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;


/**
 * Collection of String utils. 
 * 
 * @author Juergen Donnerstag
 */
public class StringUtils {

	public static String join(String separator, String... vals) {
		Args.notNull(separator, "separator");
		StringBuilder buf = new StringBuilder();
		boolean first = true;
		for (String val: vals) {
			if (val != null && val.trim().length() > 0) {
				if (!first) {
					buf.append(separator);
				}
				buf.append(val);
			}
		}
		return buf.toString();
	}

	public static String[] split(String separator, String data) {
		Args.notNull(separator, "separator");
		if (data == null) {
			return new String[0];
		}
		
		if (data.contains(separator)) {
			return data.split(separator);
		} 
		return new String[] { data };
	}

	public static List<String> split2(String separator, String data) {
		List<String> list = new ArrayList<>();
		for (String part : split(separator, data)) {
			if (part != null && part.trim().length() > 0) {
				list.add(part);
			}
		}
		return list;
	}
	
	public static String loadFromFile(File file) throws FileNotFoundException {
		Args.notNull(file, "file");
		try (Scanner scanner = new Scanner(file)) {
			// regex: \A == Beginning of input. It's a one liner to read the complete
			// content of a file into a string
			return scanner.useDelimiter("\\A").next();
		}
	}
	
	public static String loadFromFile(InputStream inputStream) throws FileNotFoundException {
		Args.notNull(inputStream, "inputStream");
		try (Scanner scanner = new Scanner(inputStream)) {
			// regex: \A == Beginning of input. It's a one liner to read the complete
			// content of a file into a string
			return scanner.useDelimiter("\\A").next();
		}
	}
	
	public static boolean isEmpty(final String data) {
		return data == null || data.isEmpty();
	}
	
	public static boolean isNotEmpty(final String data) {
		return !isEmpty(data);
	}
}
