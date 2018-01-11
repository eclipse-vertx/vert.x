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

package io.vertx.core.impl;

import java.util.HashMap;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Args {

  public final Map<String, String> map = new HashMap<>();

  public Args(String[] args) {
    String currentKey = null;
    for (String arg: args) {
      if (arg.startsWith("-")) {
        if (currentKey != null) {
          map.put(currentKey, "");
        }
        currentKey = arg;
      } else {
        if (currentKey != null) {
          map.put(currentKey, arg);
          currentKey = null;
        }
      }
    }
    if (currentKey != null) {
      map.put(currentKey, "");
    }
  }

  public int getInt(String argName) {
    String arg = map.get(argName);
    int val;
    if (arg != null) {
      try {
        val = Integer.parseInt(arg.trim());
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Invalid " + argName + ": " + arg);
      }
    } else {
      val = -1;
    }
    return val;
  }
}
