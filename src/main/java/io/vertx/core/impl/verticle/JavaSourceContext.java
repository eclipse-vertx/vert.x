/*
 * Copyright (c) 2011-2014 The original author or authors
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

package io.vertx.core.impl.verticle;

import javax.tools.JavaFileObject.Kind;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 *
 * Resolves package name and source path based on a single Java source file.
 *
 * @author Janne Hietam&auml;ki
 */
public class JavaSourceContext {

  private final String className;
  private final File sourceRoot;

  public JavaSourceContext(File file) {
    String packageName = parsePackage(file);
    File rootDirectory = file.getParentFile();
    if (packageName != null) {
      String[] pathTokens = packageName.split("\\.");
      for(int i = pathTokens.length - 1; i >= 0; i--) {
        String token = pathTokens[i];
        if (!token.equals(rootDirectory.getName())) {
          throw new RuntimeException("Package structure does not match directory structure: " + token + " != " + rootDirectory.getName());
        }
        rootDirectory = rootDirectory.getParentFile();
      }
    }
    sourceRoot = rootDirectory;

    String fileName = file.getName();
    String className = fileName.substring(0, fileName.length() - Kind.SOURCE.extension.length());
    if (packageName != null) {
      className = packageName + '.' + className;
    }
    this.className = className;
  }

  public File getSourceRoot() {
	  return sourceRoot;
  }

  public String getClassName() {
	  return className;
  }

  /*
   * Parse package definition from a Java source file:
   * First remove all comments, split file into lines, find first non-empty line
   * Then, if the line starts with keyword "package", parse the package definition from it.
   *
   */
  private static String parsePackage(File file) {
    try {
      String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      // http://stackoverflow.com/questions/1657066/java-regular-expression-finding-comments-in-code
      source = source.replaceAll( "//.*|(\"(?:\\\\[^\"]|\\\\\"|.)*?\")|(?s)/\\*.*?\\*/", "$1 " );
      for (String line : source.split("\\r?\\n")) {
        line = line.trim();
        if (!line.isEmpty()) {
          int idx = line.indexOf("package ");
          if (idx != -1) {
            return line.substring(line.indexOf(' ', idx), line.indexOf(';', idx)).trim();
          }
          return null; // Package definition must be on the first non-comment line
        }
      }
	    return null;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
