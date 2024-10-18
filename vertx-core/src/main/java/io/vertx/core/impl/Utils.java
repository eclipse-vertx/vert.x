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

package io.vertx.core.impl;

import io.netty.util.internal.PlatformDependent;

/**
 * Simple generic utility methods and constants
 *
 * @author Juergen Donnerstag
 * @author Alain Penders
 */
public class Utils {

  public static String LINE_SEPARATOR = System.getProperty("line.separator");

  private static final boolean isLinux;
  private static final boolean isWindows;
  private static final boolean isOsx;

  static {
    isLinux = "linux".equals(PlatformDependent.normalizedOs());
    isWindows = PlatformDependent.isWindows();
    isOsx = PlatformDependent.isOsx();
  }

  /**
   * @return {@code true}, if running on Linux
   */
  public static boolean isLinux() {
    return isLinux;
  }

  /**
   * @return {@code true}, if running on Windows
   */
  public static boolean isWindows() {
    return isWindows;
  }

  /**
   * @return {@code true}, if running on Mac
   */
  public static boolean isOsx() {
    return isOsx;
  }

  @SuppressWarnings("unchecked")
  public static <E extends Throwable> void throwAsUnchecked(Throwable t) throws E {
    throw (E) t;
  }
}
