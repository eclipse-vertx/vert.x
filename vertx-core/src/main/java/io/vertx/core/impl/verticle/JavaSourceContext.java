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
      String source = Files.readString(file.toPath());
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
