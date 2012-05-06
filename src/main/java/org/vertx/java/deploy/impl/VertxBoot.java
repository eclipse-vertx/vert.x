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
package org.vertx.java.deploy.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * This class relies on the existence of a vertx.properties file
 * which contains a pointer to the resources which are to be
 * loaded onto the classpath, and the real main class.
 * 
 * @author pidster
 *
 */
public class VertxBoot {

	public static void main(String... args) {
		URL[] classpath = createClasspath();
		startMain(classpath, args);
	}

	private static URL[] createClasspath() {
		
		// The minimum we need is a system property telling us
		// what our own install directory is
		Path vertx = Paths.get(System.getProperty("vertx.home"))
				.toAbsolutePath().normalize();

		// The conf directory is relative to 'vertx.home'
		Path props = vertx.resolve("conf/vertx.properties");
		
		// The lib directory is relative to 'vertx.home'
		// we'll use this to reduce the filesystem scanning
		// when loading jars
		Path lib = vertx.resolve("lib");
		
		Properties vertxProps = new Properties();

		Set<URL> found = new HashSet<URL>();

		// Use Java 7 try-with-resources
		try (InputStream is = Files.newInputStream(props, StandardOpenOption.READ)) {
			vertxProps.load(is);

			// add the loaded properties to System
			System.getProperties().putAll(vertxProps);
			
			// resolve variables
			resolvePropertyVariables(vertxProps);

			// if there's no libs this will blow up fairly quickly
			String loader = vertxProps.getProperty("vertx.libs");
			String[] paths = loader.split(",");
			
			for (String url : paths) {

				if (url.indexOf('*') > -1) {

					// TODO check this works on Windows
					if (url.startsWith(".")) {
						url = Paths.get(url).normalize().toAbsolutePath().toString();
					}
					// TODO check this works on Windows
					else if (!url.startsWith(File.separator) && !url.matches("^[A-Z]\\:\\/.*")) {
						url = resolve(vertx, url).toString();
					}

					URLMatchingGlobbedPathVisitor visitor = new URLMatchingGlobbedPathVisitor(url);
					Files.walkFileTree(lib, visitor);
					found.addAll(visitor.getMatches());
				}
				else {
					Path p = resolve(vertx, url);
					if (Files.isReadable(p)) {
						found.add(p.toUri().toURL());
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		return found.toArray(new URL[found.size()]);
	}

	/**
	 * @param vertxProps
	 */
	private static void resolvePropertyVariables(Properties properties) {
		
		Set<String> names = properties.stringPropertyNames();
		for (String name : names) {
			String modified = properties.getProperty(name);
			String value = properties.getProperty(name);

			int match = -1;
			while ((match = value.indexOf("${", match)) > -1) {

				int end = value.indexOf("}", match + 1);
				String variable = value.substring(match + 2, end);

				match = end;
				
				if (System.getProperties().containsKey(variable)) {
					modified = modified.replaceFirst("\\$\\{" + variable + "\\}", System.getProperty(variable));
				}
			}

			properties.setProperty(name, modified);
		}
	}
	
	private static Path resolve(Path base, String path) {
		return base.resolve(path).toAbsolutePath().normalize();
	}

	/**
	 * @param found
	 * @param sargs
	 * @throws ClassNotFoundException
	 * @throws NoSuchMethodException
	 * @throws IllegalAccessException
	 * @throws InvocationTargetException
	 */
	private static void startMain(URL[] resources, String[] sargs) {
		
		try {
			URLClassLoader cl = new URLClassLoader(resources, null);
			Class<?> c = cl.loadClass(System.getProperty("vertx.main"));
			Class<?>[] argTypes = { String[].class };

			Method method = c.getMethod("main", argTypes);
			method.invoke(null, (Object) sargs);

		} catch (ClassNotFoundException | NoSuchMethodException
				| SecurityException | IllegalAccessException
				| IllegalArgumentException | InvocationTargetException e) {

			throw new RuntimeException(e);
		}
	}

	private static class URLMatchingGlobbedPathVisitor extends SimpleFileVisitor<Path> {

		private final Set<URL> matches;

		private final PathMatcher matcher;
		
		public URLMatchingGlobbedPathVisitor(String glob) {
			this.matches = new HashSet<URL>();
			this.matcher = FileSystems.getDefault()
					.getPathMatcher(String.format("glob:%s", glob));
		}

		@Override
		public FileVisitResult visitFile(Path file,
				BasicFileAttributes attrs) throws IOException {

			if (matcher.matches(file) && Files.isReadable(file)) {
				matches.add(file.toUri().toURL());
			}

			return FileVisitResult.CONTINUE;
		}
		
		public Set<URL> getMatches() {
			return matches;
		}
	}

}
