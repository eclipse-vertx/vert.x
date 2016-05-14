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
package io.vertx.core.impl.launcher.commands;

import java.util.List;

/**
 * A couple of utility methods easing process creation.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class ExecUtils {

  private static final String SINGLE_QUOTE = "\'";
  private static final String DOUBLE_QUOTE = "\"";

  /**
   * The {@code os.name} property is mandatory (from the Java Virtual Machine specification).
   */
  private static String osName = System.getProperty("os.name").toLowerCase();

  /**
   * Puts quotes around the given String if necessary.
   * <p>
   * If the argument doesn't include spaces or quotes, return it as is. If it
   * contains double quotes, use single quotes - else surround the argument by
   * double quotes.
   * </p>
   *
   * @param argument the argument to be quoted
   * @return the quoted argument
   * @throws IllegalArgumentException If argument contains both types of quotes
   */
  public static String quoteArgument(final String argument) {

    String cleanedArgument = argument.trim();

    // strip the quotes from both ends
    while (cleanedArgument.startsWith(SINGLE_QUOTE) && cleanedArgument.endsWith(SINGLE_QUOTE)
        || cleanedArgument.startsWith(DOUBLE_QUOTE)  && cleanedArgument.endsWith(DOUBLE_QUOTE)) {
      cleanedArgument = cleanedArgument.substring(1, cleanedArgument.length() - 1);
    }

    final StringBuilder buf = new StringBuilder();
    if (cleanedArgument.contains(DOUBLE_QUOTE)) {
      if (cleanedArgument.contains(SINGLE_QUOTE)) {
        throw new IllegalArgumentException(
            "Can't handle single and double quotes in same argument");
      }
      return buf.append(SINGLE_QUOTE).append(cleanedArgument).append(
          SINGLE_QUOTE).toString();
    } else if (cleanedArgument.contains(SINGLE_QUOTE)
        || cleanedArgument.contains(" ")) {
      return buf.append(DOUBLE_QUOTE).append(cleanedArgument).append(
          DOUBLE_QUOTE).toString();
    } else {
      return cleanedArgument;
    }
  }

  /**
   * Adds an argument to the given list. It automatically adds quotes to the argument if necessary.
   *
   * @param args the list of arguments
   * @param argument the argument to add
   */
  public static void addArgument(List<String> args, String argument) {
    args.add(quoteArgument(argument));
  }

  /**
   * @return {@code true} if the current operating system belongs to the "windows" family.
   */
  public static boolean isWindows() {
    return osName.contains("windows");
  }

  /**
   * Exits the JVM with the given exit code.
   * @param code the code, {@code 0} for success. By convention a non zero value if return to denotes an
   *             error.
   */
  public static void exit(int code) {
    System.exit(code);
  }

  /**
   * Exits the JVM and indicate an issue during the Vert.x initialization.
   */
  public static void exitBecauseOfVertxInitializationIssue() {
    exit(1);
  }

  /**
   * Exits the JVM and indicate an issue during the deployment of the main verticle.
   */
  public static void exitBecauseOfVertxDeploymentIssue() {
    exit(5);
  }

  /**
   * Exits the JVM and indicate an issue with a process creation or termination.
   */
  public static void exitBecauseOfProcessIssue() {
    exit(2);
  }

  /**
   * Exits the JVM and indicate an issue with the system configuration.
   */
  public static void exitBecauseOfSystemConfigurationIssue() {
    exit(3);
  }


}
