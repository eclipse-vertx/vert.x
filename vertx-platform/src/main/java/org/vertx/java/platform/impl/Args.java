/*
 * Copyright 2011-2012 the original author or authors.
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

package org.vertx.java.platform.impl;

import java.util.HashMap;
import java.util.Map;

/**
 * Parses args of the form -x y
 *
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
          currentKey = null;
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
