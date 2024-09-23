/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.verticle;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;

import static io.vertx.core.internal.net.RFC3986.decodeURIComponent;

/**
 * @author Janne Hietam&auml;ki
 */

public class PackageHelper {
  private final static String CLASS_FILE = ".class";

  private final ClassLoader classLoader;

  public PackageHelper(ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public List<JavaFileObject> find(String packageName) throws IOException {
    String javaPackageName = packageName.replaceAll("\\.", "/");

    List<JavaFileObject> result = new ArrayList<>();

    Enumeration<URL> urlEnumeration = classLoader.getResources(javaPackageName);
    while (urlEnumeration.hasMoreElements()) {
      URL resource = urlEnumeration.nextElement();
      //Need to urldecode it too, since bug in JDK URL class which does not url decode it, so if it contains spaces you are screwed
      final File directory = new File(decodeURIComponent(resource.getFile(), false));
      if (directory.isDirectory()) {
        result.addAll(browseDir(packageName, directory));
      } else {
        result.addAll(browseJar(resource));
      }
    }
    return result;
  }

  private static List<JavaFileObject> browseDir(String packageName, File directory) {
    List<JavaFileObject> result = new ArrayList<>();
    for (File childFile : directory.listFiles()) {
      if (childFile.isFile() && childFile.getName().endsWith(CLASS_FILE)) {
        String binaryName = packageName + "." + childFile.getName().replaceAll(CLASS_FILE + "$", "");
        result.add(new CustomJavaFileObject(childFile.toURI(), JavaFileObject.Kind.CLASS, binaryName));
      }
    }
    return result;
  }

  private static List<JavaFileObject> browseJar(URL packageFolderURL) {
    List<JavaFileObject> result = new ArrayList<>();
    try {
      String jarUri = packageFolderURL.toExternalForm().split("!")[0];
      JarURLConnection jarConn = (JarURLConnection) packageFolderURL.openConnection();
      String rootEntryName = jarConn.getEntryName();
      int rootEnd = rootEntryName.length() + 1;

      Enumeration<JarEntry> entryEnum = jarConn.getJarFile().entries();
      while (entryEnum.hasMoreElements()) {
        JarEntry jarEntry = entryEnum.nextElement();
        String name = jarEntry.getName();
        if (name.startsWith(rootEntryName) && name.indexOf('/', rootEnd) == -1 && name.endsWith(CLASS_FILE)) {
          String binaryName = name.replaceAll("/", ".").replaceAll(CLASS_FILE + "$", "");
          result.add(new CustomJavaFileObject(URI.create(jarUri + "!/" + name), JavaFileObject.Kind.CLASS, binaryName));
        }
      }
    } catch (Exception e) {
      throw new RuntimeException(packageFolderURL + " is not a JAR file", e);
    }
    return result;
  }
}
