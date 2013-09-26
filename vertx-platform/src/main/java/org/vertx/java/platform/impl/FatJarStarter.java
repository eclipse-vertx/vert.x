/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 */

package org.vertx.java.platform.impl;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


/**
 * This class is executed whan a Vert.x fat jar is run.
 * When it is run there is no access to any libraries other than JDK libs, so we can't depend on any Vert.x
 * or other classes here.
 * The first thing we do is unzip the fat jar into a temporary directory.
 * Then basically we need to create a class loader that can see the vert.x libs in the lib directory and
 * load the Vert.x Starter class using that.
 * We then execute vertx runmod module_name args using that
 */
public class FatJarStarter extends Thread {

  private static final String TEMP_DIR = System.getProperty("java.io.tmpdir");
  private static final String FILE_SEP = System.getProperty("file.separator");
  private static final int BUFFER_SIZE = 4096;

  private File vertxHome;

  public static void main(String[] args) {
    try {
      new FatJarStarter().go(args);
    } catch (Exception e) {
      System.err.println("Failed to run fat jar");
      e.printStackTrace();
    }
  }

  private FatJarStarter() {
    Runtime.getRuntime().addShutdownHook(this);
  }

  private void go(String[] args) throws Exception {
    URLClassLoader urlc = (URLClassLoader)FatJarStarter.class.getClassLoader();

    String fileName = urlc.getURLs()[0].getFile();

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
    for (File file: files) {
      if (file.getName().endsWith(".jar") || file.getName().endsWith(".zip")) {
        urls.add(file.toURI().toURL());
      }
    }

    // And create the class loader

    URLClassLoader platformLoader = new URLClassLoader(urls.toArray(new URL[urls.size()]), urlc.getParent());
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
    System.setProperty("vertx.clusterManagerFactory", "org.vertx.java.spi.cluster.impl.hazelcast.HazelcastClusterManagerFactory");

    // Get the main method

    Method meth = starterClass.getMethod("main", new Class[] { String[].class });

    // Invoke it

    String[] theargs = largs.toArray(new String[largs.size()]);
    meth.invoke(null, (Object)theargs);
  }

  // Shutdown hook
  public void run() {
    try {
      deleteDir(vertxHome);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private String generateTmpFileName() {
    return TEMP_DIR + FILE_SEP + "vertx-" + UUID.randomUUID().toString();
  }

  private File unzipIntoTmpDir(String fileName) throws Exception {
    String tdir = generateTmpFileName();
    File tdest = new File(tdir);
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
            if (!new File(directory, entryName).mkdir()) {
              throw new IllegalStateException("Failed to create directory");
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
