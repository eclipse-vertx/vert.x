/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.impl.launcher.commands;

import io.vertx.core.impl.Utils;

import java.util.List;

/**
 * A couple of utility methods easing process creation.
 *
 * @author Clement Escoffier <clement@apache.org>
 */
public class ExecUtils {

  private static final String SINGLE_QUOTE = "\'";
  private static final String DOUBLE_QUOTE = "\"";


  // A note about exit code
  // * 0 for success
  // * 1 for general error
  // * exit codes 1-2, 126-165, and 255 have special meanings, and should therefore be avoided for user-specified
  // exit parameters
  // * Ending with exit 127 would certainly cause confusion when troubleshooting as it's the command not found code
  // * 130, 134, 133, 135, 137, 140, 129, 142, 143, 145, 146, 149, 150, 152, 154, 155, 158 are used by the JVM, and
  // so have special meanings
  // * it's better to avoid 3-9

  /**
   * Error code used when vert.x cannot be initialized.
   */
  public static final int VERTX_INITIALIZATION_EXIT_CODE = 11;

  /**
   * Error code used when a deployment failed.
   */
  public static final int VERTX_DEPLOYMENT_EXIT_CODE = 15;

  /**
   * Error code used when a spawn process cannot be found, created, or stopped smoothly.
   */
  public static final int PROCESS_ERROR_EXIT_CODE = 12;

  /**
   * Error code used when the system configuration does not satisfy the requirements (java not found for example).
   */
  public static final int SYSTEM_CONFIGURATION_EXIT_CODE = 14;

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
        || cleanedArgument.startsWith(DOUBLE_QUOTE) && cleanedArgument.endsWith(DOUBLE_QUOTE)) {
      cleanedArgument = cleanedArgument.substring(1, cleanedArgument.length() - 1);
    }

    final StringBuilder buf = new StringBuilder();
    if (cleanedArgument.contains(DOUBLE_QUOTE)) {
      if (cleanedArgument.contains(SINGLE_QUOTE)) {
        throw new IllegalArgumentException(
            "Can't handle single and double quotes in same argument");
      }
      if (Utils.isWindows()) {
        return buf.append(DOUBLE_QUOTE).append(cleanedArgument.replace("\"", "\\\"")).append(DOUBLE_QUOTE).toString();
      } else {
        return buf.append(SINGLE_QUOTE).append(cleanedArgument).append(
          SINGLE_QUOTE).toString();
      }
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
   * @param args     the list of arguments
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
   * @return {@code true} if the current operating system belongs to the "linux" family.
   */
  public static boolean isLinux() {
    return osName.contains("nux");
  }

  /**
   * Exits the JVM with the given exit code.
   *
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
    exit(VERTX_INITIALIZATION_EXIT_CODE);
  }

  /**
   * Exits the JVM and indicate an issue during the deployment of the main verticle.
   */
  public static void exitBecauseOfVertxDeploymentIssue() {
    exit(VERTX_DEPLOYMENT_EXIT_CODE);
  }

  /**
   * Exits the JVM and indicate an issue with a process creation or termination.
   */
  public static void exitBecauseOfProcessIssue() {
    exit(PROCESS_ERROR_EXIT_CODE);
  }

  /**
   * Exits the JVM and indicate an issue with the system configuration.
   */
  public static void exitBecauseOfSystemConfigurationIssue() {
    exit(SYSTEM_CONFIGURATION_EXIT_CODE);
  }


}
