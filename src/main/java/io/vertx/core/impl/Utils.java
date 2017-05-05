/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.impl;

/**
 * Simple generic utility methods and constants
 *
 * @author Juergen Donnerstag
 * @author Alain Penders
 */
// TODO: 17/1/1 by zmyer
public class Utils {

    //换行符
    public static String LINE_SEPARATOR = System.getProperty("line.separator");

    //window标记
    private static final boolean isWindows;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        isWindows = os.contains("win");
    }

    /**
     * @return true, if running on Windows
     */
    public static boolean isWindows() {
        return isWindows;
    }

}
