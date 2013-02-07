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
package org.vertx.java.platform.impl.java;

import javax.tools.JavaFileObject;
import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;

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

    List<JavaFileObject> result = new ArrayList<JavaFileObject>();

    Enumeration<URL> urlEnumeration = classLoader.getResources(javaPackageName);
    while (urlEnumeration.hasMoreElements()) {
      URL resource = urlEnumeration.nextElement();
      //Need to urldecode it too, since bug in JDK URL class which does not url decode it, so if it contains spaces you are screwed
      File directory;
      try {
        directory = new File(URLDecoder.decode(resource.getFile(), "UTF-8"));
      } catch (UnsupportedEncodingException e) {
        throw new IllegalStateException("Failed to decode " + e.getMessage());
      }
      if (directory.isDirectory()) {
        result.addAll(browseDir(packageName, directory));
      } else {
        result.addAll(browseJar(resource));
      }
    }
    return result;
  }

  private List<JavaFileObject> browseDir(String packageName, File directory) {
    List<JavaFileObject> result = new ArrayList<JavaFileObject>();
    for (File childFile : directory.listFiles()) {
      if (childFile.isFile() && childFile.getName().endsWith(CLASS_FILE)) {
        String binaryName = packageName + "." + childFile.getName().replaceAll(CLASS_FILE + "$", "");
        result.add(new CustomJavaFileObject(childFile.toURI(), JavaFileObject.Kind.CLASS, binaryName));
      }
    }
    return result;
  }

  private List<JavaFileObject> browseJar(URL packageFolderURL) {
    List<JavaFileObject> result = new ArrayList<JavaFileObject>();
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