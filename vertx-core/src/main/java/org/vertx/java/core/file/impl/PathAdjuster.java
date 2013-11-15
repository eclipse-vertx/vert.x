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

package org.vertx.java.core.file.impl;

import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.impl.VertxInternal;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PathAdjuster {

  public static Path adjust(final VertxInternal vertx, Path path) {
    DefaultContext context = vertx.getContext();
    if (context != null) {
      PathResolver resolver = context.getPathResolver();
      if (resolver != null) {
        return resolver.resolve(path);
      }
    }
    return path;
  }

  public static String adjust(final VertxInternal vertx, String path) {
    Path adjustment = adjust(vertx, Paths.get(path));
    return adjustment.toString();
  }
}
