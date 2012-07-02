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
package org.vertx.java.boot;

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
 * Requires the system variables: vertx.home and vertx.conf
 *
 * @author pidster
 *
 */
public class VertxBoot {

    private static final boolean DEBUG = Boolean.getBoolean("vertx.boot.debug");

    public static void main(String... args) {

        if (DEBUG) {
            System.out.println("vert.x starting with DEBUG=true");
        }

        URL[] classpath = createClasspath();

        if (DEBUG) {
            for (URL url : classpath) {
                System.out.format("url:%s %n", url);
            }
        }

        startMain(classpath, args);
    }

    private static URL[] createClasspath() {

        // The minimum we need is a system property telling us
        // what our own install directory is
        Path userDir = stringToPath(System.getProperty("user.dir"));

        // The minimum we need is a system property telling us
        // what our own install directory is
        Path vertx = stringToPath(System.getProperty("vertx.install"));
        System.setProperty("vertx.home", vertx.toString());
        System.setProperty("vertx.install", vertx.toString());

        // The conf directory is relative to 'vertx.home'
        String vertxConf = System.getProperty("vertx.conf", "conf/vertx.properties");
        Path props = vertx.resolve(vertxConf);
        if (!Files.isReadable(props)) {
            throw new RuntimeException("vertx.properties file missing or unreadable!");
        }

        Properties vertxProps = new Properties();

        if (DEBUG) {
            System.out.format("vertx.install: %s %n", vertx);
        }

        Set<URL> found = new HashSet<URL>();

        // Use Java 7 try-with-resources
        try (InputStream is = Files.newInputStream(props, StandardOpenOption.READ)) {
            vertxProps.load(is);

            // resolve variables
            resolvePropertyVariables(vertxProps);

            // add the loaded & translate properties to System
            System.getProperties().putAll(vertxProps);

            // if there's no libs this will blow up fairly quickly
            String loader = vertxProps.getProperty("vertx.libs");
            String[] paths = loader.split(",");

            for (String url : paths) {

                if (DEBUG) {
                    System.out.format("url:%s %n", url);
                }

                // TODO check this works on Windows
                if (url.startsWith(".")) {
                    url = stringToPath(url).toString(); // resolve against relative path
                }

                // TODO check this works on Windows
                if (!url.startsWith(File.separator) && !url.matches("^[A-Z]\\:\\/.*")) {
                    url = resolve(userDir, url).toString(); // resolve against current dir
                }

                if (url.indexOf('*') > -1) {
                    URLMatchingGlobbedPathVisitor visitor = new URLMatchingGlobbedPathVisitor(url);
                    Files.walkFileTree(vertx, visitor); // relative to vertx.home
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

    private static Path resolve(Path base, String input) {
    	if (input == null)
    		throw new RuntimeException("'input' must not be null!");
        return base.resolve(input).toAbsolutePath().normalize();
    }

    private static Path stringToPath(String input) {
    	if (input == null)
    		throw new RuntimeException("'input' must not be null!");
        return Paths.get(input).toAbsolutePath().normalize();
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
        	ClassLoader tccl = Thread.currentThread().getContextClassLoader();
            URLClassLoader cl = new URLClassLoader(resources, tccl);
            Thread.currentThread().setContextClassLoader(cl);

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
                if (DEBUG) {
                    System.out.format("path: %s %n", file);
                }
                matches.add(file.toUri().toURL());
            }

            return FileVisitResult.CONTINUE;
        }

        public Set<URL> getMatches() {
            return matches;
        }
    }
}