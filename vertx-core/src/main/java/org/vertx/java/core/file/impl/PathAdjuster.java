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

package org.vertx.java.core.file.impl;

import org.vertx.java.core.impl.Context;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PathAdjuster {

  public static Path adjust(Path path) {
    Context context = Context.getContext();
    if (context != null) {
      Path adjustment = context.getPathAdjustment();
      if (adjustment != null) {
        return adjustment.resolve(path);
      }
    }
    return path;
  }

  public static String adjust(String path) {
    Path adjustment = adjust(Paths.get(path));
    return adjustment.toString();
  }
}
