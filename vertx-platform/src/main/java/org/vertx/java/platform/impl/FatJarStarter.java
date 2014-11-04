/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl;

import java.io.*;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * This class is executed when a Vert.x fat jar is run.
 * When it is run there is no access to any libraries other than JDK libs, so we can't depend on any Vert.x
 * or other classes here.
 * The first thing we do is unzip the fat jar into a temporary directory.
 * Then basically we need to create a class loader that can see the vert.x libs in the lib directory and
 * load the Vert.x Starter class using that.
 * We then execute vertx runmod module_name args using that
 */
public class FatJarStarter implements Runnable {

  private static final String CP_SEPARATOR = System.getProperty("path.separator");
  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String CLUSTERMANAGER_FACTORY_PROP_NAME = "vertx.clusterManagerFactory";
  private static final int BUFFER_SIZE = 4096;

  private File vertxHome;
  private URLClassLoader platformLoader;

  public static void main(String[] args) {
    try {
      new FatJarStarter().go(args);
    } catch (Exception e) {
      System.err.println("Failed to run fat jar");
      e.printStackTrace();
    }
  }

  private FatJarStarter() {
  }

  private void go(String[] args) throws Exception {
    URLClassLoader urlc = (URLClassLoader)FatJarStarter.class.getClassLoader();

    // file name representing this class inside the jar
    String classFilename = FatJarStarter.class.getName().replace(".", "/") + ".class";
    // class url in the form jar:file:/jarfile.jar!/classFilename.class
    URL classURL = urlc.getResource(classFilename);
    if (!classURL.getProtocol().equals("jar")) {
      throw new IllegalStateException("Failed to find jar file, classURL is " + classURL.toString());
    }
    // string of the jar filename in the form /jarfile.jar!/classFilename
    String jarName = new URL(classURL.getFile()).getPath();
    // cut off the ! and convert to local filename representation
    String fileName = new URI(jarName.substring(0, jarName.lastIndexOf('!'))).getPath();

    // Look for -cp or -classpath parameter
    String classpath = null;
    boolean hasCP = false;
    for (String arg: args) {
      if (hasCP) {
        classpath = arg;
        break;
      } else if ("-cp".equals(arg) || "-classpath".equals(arg)) {
        hasCP = true;
      }
    }

    // Unzip into temp directory

    vertxHome = unzipIntoTmpDir(fileName);

    // Load module id

    File modsDir = new File(vertxHome, "mods");

    File manifest = new File(new File(vertxHome, "META-INF"), "MANIFEST.MF");
    String moduleID = null;
    try (Scanner scanner = new Scanner(manifest)) {
      while (scanner.hasNextLine()) {
        String line = scanner.nextLine();
        if (line.startsWith("Vertx-Module-ID")) {
          moduleID = line.substring(line.lastIndexOf(':') + 1).trim();
        }
      }
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read module id from manifest", e);
    }
    if (moduleID == null) {
      throw new NullPointerException("moduleID");
    }

    // Create class loader to load the platform itself

    // Now generate the classpath

    File libDir = new File(vertxHome, "lib");

    File[] files = libDir.listFiles();

    List<URL> urls = new ArrayList<>();

    if (classpath != null) {
      // Add any extra classpath to the beginning - this allows the user to specify classpath to say,
      // a custom cluster.xml on the command line when running the jar e.g.
      // java -jar my-mod~1.0-fat.jar -cp path/to/my/clusterxml
      urls.addAll(splitCP(classpath));
    }

    for (File file: files) {
      if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
        urls.add(file.toURI().toURL());
      }
    }

    // You can also add resources in a directory called "platform_lib" and this will be added to the
    // platform claspath - so you can use it to put cluster.xml or any logging libraries that are needed
    // on a platform level
    File platformLibDir = new File(new File(modsDir, moduleID), "platform_lib");

    urls.add(platformLibDir.toURI().toURL());
    if (platformLibDir.exists()) {
      files = platformLibDir.listFiles();
      if (files != null) {
        for (File file: files) {
          if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
            urls.add(file.toURI().toURL());
          }
        }
      }
    }

    // And create the class loader

    platformLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), urlc.getParent());
    Thread.currentThread().setContextClassLoader(platformLoader);
    Class<?> starterClass = platformLoader.loadClass("org.vertx.java.platform.impl.cli.Starter");


    // Create the args to be passed into Starter
    // We basically execute a vertx runmod <module_name> <args_from_command_line...>

    List<String> largs = new ArrayList<>();
    largs.add("runmod");
    largs.add(moduleID);

    boolean ignoring = false;
    for (String arg: args) {
      // Remove any cp argument
      if (arg.equals("-cp")) {
        ignoring = true;
      } else if (!ignoring) {
        largs.add(arg);
      } else {
        ignoring = false;
      }
    }

    // Set sys props

    System.setProperty("vertx.home", vertxHome.getAbsolutePath());
    System.setProperty("vertx.mods", modsDir.getAbsolutePath());
    if (System.getProperty(CLUSTERMANAGER_FACTORY_PROP_NAME) == null) {
      System.setProperty(CLUSTERMANAGER_FACTORY_PROP_NAME, "org.vertx.java.spi.cluster.impl.hazelcast.HazelcastClusterManagerFactory");
    }

    // Add after shutdown task


    Method afterShutdownMeth = starterClass.getMethod("addAfterShutdownTask", new Class[] { Runnable.class });
    afterShutdownMeth.invoke(null, this);

    // Get the main method

    Method meth = starterClass.getMethod("main", new Class[] { String[].class });

    // Invoke it

    String[] theargs = largs.toArray(new String[largs.size()]);
    meth.invoke(null, (Object)theargs);
  }

  private List<URL> splitCP(String cp) {
    String[] parts;
    if (cp.contains(CP_SEPARATOR)) {
      parts = cp.split(CP_SEPARATOR);
    } else {
      parts = new String[] { cp };
    }
    List<URL> classpath = new ArrayList<>();
    for (String part: parts) {
      try {
        URL url = new File(part).toURI().toURL();
        classpath.add(url);
      } catch (MalformedURLException e) {
        throw new IllegalArgumentException("Invalid path " + part + " in cp " + cp);
      }
    }
    return classpath;
  }

  // Shutdown hook
  public void run() {
    try {
      platformLoader.close();
      deleteDir(vertxHome);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private File generateTmpFileName() {
    return new File(TEMP_DIR, "vertx-" + UUID.randomUUID().toString());
  }

  private File unzipIntoTmpDir(String fileName) throws Exception {
    File tdest = generateTmpFileName();
    if (!tdest.mkdir()) {
      throw new IllegalStateException("Failed to create directory " + tdest);
    }
    unzipJar(tdest, fileName);
    return tdest;
  }

  private void unzipJar(final File directory, final String jarName) throws Exception {
    try (InputStream is = new BufferedInputStream(new FileInputStream(jarName)); ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
      ZipEntry entry;
      while ((entry = zis.getNextEntry()) != null) {
        String entryName = entry.getName();
        if (!entryName.isEmpty()) {
          if (entry.isDirectory()) {
            if (!new File(directory, entryName).exists()) {
              if (!new File(directory, entryName).mkdir()) {
                throw new IllegalStateException("Failed to create directory");
              }
            }
          } else {
            int count;
            byte[] buff = new byte[BUFFER_SIZE];
            BufferedOutputStream dest = null;
            try {
              File fentry = new File(directory, entryName);
              File dir = fentry.getParentFile();
              if (!dir.exists()) {
                dir.mkdirs();
              }
              OutputStream fos = new FileOutputStream(fentry);
              dest = new BufferedOutputStream(fos, BUFFER_SIZE);
              while ((count = zis.read(buff, 0, BUFFER_SIZE)) != -1) {
                dest.write(buff, 0, count);
              }
              dest.flush();
            } finally {
              if (dest != null) {
                dest.close();
              }
            }
          }
        }
      }
    }
  }

  private void deleteDir(File file) throws Exception {
    final Path source = file.toPath();
    Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        Files.delete(file);
        return FileVisitResult.CONTINUE;
      }
      public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
        if (e == null) {
          Files.delete(dir);
          return FileVisitResult.CONTINUE;
        } else {
          throw e;
        }
      }
    });
  }

}
