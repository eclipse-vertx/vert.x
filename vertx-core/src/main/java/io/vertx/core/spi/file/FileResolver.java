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

package io.vertx.core.spi.file;

import io.vertx.core.file.FileSystemOptions;
import io.vertx.core.file.impl.FileResolverImpl;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * Sometimes the file resources of an application are bundled into jars, or are somewhere on the classpath but not
 * available on the file system, e.g. in the case of a Vert.x webapp bundled as a fat jar.
 * <p>
 * In this case we want the application to access the resource from the classpath as if it was on the file system.
 * <p>
 * We can do this by looking for the file on the classpath, and if found, copying it to a temporary cache directory
 * on disk and serving it from there.
 * <p>
 * There is one cache dir per Vert.x instance which is deleted on Vert.x shutdown.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @author <a href="https://github.com/rworsnop/">Rob Worsnop</a>
 */
public interface FileResolver extends Closeable {

  /**
   * Create a file resolver.
   *
   * @param options the fs options
   * @return the file resolver
   */
  static FileResolver fileResolver(FileSystemOptions options) {
    return new FileResolverImpl(options);
  }

  /**
   * Resolve the file for the specified {@code fileName}.
   *
   * This method should never return {@code null}, it can return a file that does not exist.
   *
   * @param fileName the name to resolve
   * @return a file resolved
   */
  File resolve(String fileName);

  /**
   * Close this file resolver, this is a blocking operation.
   */
  void close() throws IOException;

}
