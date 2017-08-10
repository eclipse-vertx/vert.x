package io.vertx.core.impl.cpu;
/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Locale;

/**
 * Provides general information about the processors on this host.
 *
 * @author Jason T. Greene
 */
public class CpuCoreSensor {

    private static final String CPUS_ALLOWED = "Cpus_allowed:";
    private static final byte[] BITS = new byte[]{0, 1, 1, 2, 1, 2, 2, 3, 1, 2, 2, 3, 2, 3, 3, 4};
    private static final Charset ASCII = Charset.forName("US-ASCII");

    /**
     * Returns the number of processors available to this process. On most operating systems this method
     * simply delegates to {@link Runtime#availableProcessors()}. However, on Linux, this strategy
     * is insufficient, since the JVM does not take into consideration the process' CPU set affinity
     * which is employed by cgroups and numactl. Therefore this method will analyze the Linux proc filesystem
     * to make the determination. Since the CPU affinity of a process can be change at any time, this method does
     * not cache the result. Calls should be limited accordingly.
     * <br>
     * Note tha on Linux, both SMT units (Hyper-Threading) and CPU cores are counted as a processor.
     *
     * @return the available processors on this system.
     */
    public static int availableProcessors() {
        if (System.getSecurityManager() != null) {
            return AccessController.doPrivileged((PrivilegedAction<Integer>) () -> Integer.valueOf(determineProcessors())).intValue();
        }

        return determineProcessors();
    }

    private static int determineProcessors() {
        int javaProcs = Runtime.getRuntime().availableProcessors();
        if (!isLinux()) {
            return javaProcs;
        }

        int maskProcs = 0;

        try {
            maskProcs = readCPUMask();
        } catch (Exception e) {
            // yum
        }

        return maskProcs > 0 ? Math.min(javaProcs, maskProcs) : javaProcs;
    }

    private static int readCPUMask() throws IOException {
        final FileInputStream stream = new FileInputStream("/proc/self/status");
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

    private static boolean isLinux() {
        String osArch = System.getProperty("os.name", "unknown").toLowerCase(Locale.US);
        return (osArch.contains("linux"));
    }
}
