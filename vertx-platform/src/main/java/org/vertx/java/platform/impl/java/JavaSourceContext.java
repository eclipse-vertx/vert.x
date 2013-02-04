/*
 * Copyright 2013 the original author or authors.
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
  private final static String REMOVE_COMMENTS_REGEXP = "(?://.*)|(/\\*(?:.|[\\n\\r])*?\\*/)";

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
      className = packageName + "." + className;
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
  private String parsePackage(File file) {
    try {
      String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      source = source.replaceAll(REMOVE_COMMENTS_REGEXP, " ");
      for (String line : source.split("\\r?\\n")) {
        line = line.trim();
        if (line.length() > 0) {
          int idx = line.indexOf("package ");
          if (idx != -1) {
            return line.substring(line.indexOf(" ", idx), line.indexOf(";", idx)).trim();
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
