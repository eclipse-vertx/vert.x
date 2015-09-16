/*
 *  Copyright (c) 2011-2015 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *       The Eclipse Public License is available at
 *       http://www.eclipse.org/legal/epl-v10.html
 *
 *       The Apache License v2.0 is available at
 *       http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.impl.launcher;

import java.io.File;

/**
 * Utilities method to analyse original command line.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class CommandLineUtils {

  /**
   * @return the fat-jar file used to execute the application if the fat-jar approach is used.
   */
  public static String getJar() {
    // Check whether or not the "sun.java.command" system property is defined,
    // if it is, check whether the first segment of the command ends with ".jar".
    String segment = getFirstSegmentOfCommand();
    if (segment != null && segment.endsWith(".jar")) {
      return segment;
    } else {
      // Second attend is to check the classpath. If the classpath contains only one element,
      // it's the fat jar
      String classpath = System.getProperty("java.class.path");
      if (!classpath.isEmpty() && !classpath.contains(File.pathSeparator) && classpath.endsWith(".jar")) {
        return classpath;
      }
    }

    return null;

  }

  /**
   * @return try to get the command line having launched the application using the {@code sun.java.command}
   * system properties. {@code null} if not set.
   */
  public static String getCommand() {
    return System.getProperty("sun.java.command");
  }

  /**
   * @return the first segment of the command line.
   */
  public static String getFirstSegmentOfCommand() {
    String cmd = getCommand();
    if (cmd != null) {
      String[] segments = cmd.split(" ");
      if (segments.length >= 1) {
        return segments[0];
      }
    }
    return null;
  }
}
