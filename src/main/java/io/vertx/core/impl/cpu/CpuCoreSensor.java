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

package io.vertx.core.impl.cpu;

import io.vertx.core.impl.launcher.commands.ExecUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Utility class providing the number of CPU cores available. On Linux, to handle CGroups, it reads and
 * parses the /proc/self/status file.
 * <p>
 * This class derives from https://github.com/wildfly/wildfly-common/blob/master/src/main/java/org/wildfly/common/cpu/ProcessorInfo.java
 * licensed under the Apache Software License 2.0.
 *
 * @author Jason T. Greene
 * @author <a href="http://escoffier.me">Clement Escoffier</a>
 */
public class CpuCoreSensor {

  private static final String CPUS_ALLOWED = "Cpus_allowed:";
  private static final byte[] BITS = new byte[]{0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4};
  private static final Charset ASCII = Charset.forName("US-ASCII");

  /**
   * Returns the number of processors available to this process.
   * <p>
   * On most operating systems this method simply delegates to {@link Runtime#availableProcessors()}. However, on
   * Linux, this strategy is insufficient, since the JVM does not take into consideration the process' CPU set affinity
   * which is employed by cgroups and numactl. Therefore this method will analyze the Linux proc filesystem
   * to make the determination.
   * <p>
   * Since the CPU affinity of a process can be change at any time, this method does not cache the result.
   * <p>
   * Note that on Linux, both SMT units (Hyper-Threading) and CPU cores are counted as a processor.
   * <p>
   * This method is used to configure the default number of event loops. This settings can be overridden by the user.
   *
   * @return the available processors on this system.
   */
  public static int availableProcessors() {
    if (System.getSecurityManager() != null) {
      return AccessController.doPrivileged((PrivilegedAction<Integer>) () -> (determineProcessors()));
    }

    return determineProcessors();
  }

  private static int determineProcessors() {
    int fromJava = Runtime.getRuntime().availableProcessors();
    int fromProcFile = 0;

    if (!ExecUtils.isLinux()) {
      return fromJava;
    }

    try {
      fromProcFile = readCPUMask(new File("/proc/self/status"));
    } catch (Exception e) {
      // We can't do much at this point, we are on linux but using a different /proc format.
    }

    return fromProcFile > 0 ? Math.min(fromJava, fromProcFile) : fromJava;
  }

  protected static int readCPUMask(File file) throws IOException {
    if (file == null  || ! file.exists()) {
      return -1;
    }

    final FileInputStream stream = new FileInputStream(file);
    final InputStreamReader inputReader = new InputStreamReader(stream, ASCII);

    try (BufferedReader reader = new BufferedReader(inputReader)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.startsWith(CPUS_ALLOWED)) {
          int count = 0;
          int start = CPUS_ALLOWED.length();
          for (int i = start; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch >= '0' && ch <= '9') {
              count += BITS[ch - '0'];
            } else if (ch >= 'a' && ch <= 'f') {
              count += BITS[ch - 'a' + 10];
            } else if (ch >= 'A' && ch <= 'F') {
              count += BITS[ch - 'A' + 10];
            }
          }
          return count;
        }
      }
    }

    return -1;
  }
}
