/*
 * Copyright (c) 2011-2014 The original author or authors
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

/**
 * Sometimes the file resources of an application are bundled into jars, or are somewhere on the classpath but not
 * available on the file system, e.g. in the case of a Vert.x webapp bundled as a fat jar.
 *
 * In this case we want the application to access the resource from the classpath as if it was on the file system.
 *
 * We can do this by looking for the file on the classpath, and if found, copying it to a temporary cache directory
 * on disk and serving it from there.
 *
 * There is one cache dir per Vert.x instance and they are deleted on Vert.x shutdown.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class FileResolver {

  private final Vertx vertx;
  private File cacheDir;

  private void setupCacheDir() {
    if (cacheDir == null) {
      String cacheDirName = ".vertx/file-cache-" + UUID.randomUUID().toString();
      cacheDir = new File(cacheDirName);
      if (cacheDir.exists()) {
        vertx.fileSystem().deleteSyncRecursive(cacheDir.getAbsolutePath(), true);
      } else {
        if (!cacheDir.mkdirs()) {
          throw new IllegalStateException("Failed to create cache dir");
        }
      }
    }
  }

  public FileResolver(Vertx vertx) {
    this.vertx = vertx;
  }

  public void deleteCacheDir(Handler<AsyncResult<Void>> handler) {
    if (cacheDir != null) {
      vertx.fileSystem().deleteRecursive(cacheDir.getAbsolutePath(), true, handler);
    } else {
      handler.handle(Future.completedFuture());
    }
  }

  public File resolveFile(String fileName) {
    // First look for file with that name on disk
    File file = new File(fileName);
    if (!file.exists()) {
      // Look for it in local file cache
      File cacheFile = null;
      if (cacheDir != null) {
        cacheFile = new File(cacheDir, fileName);
        if (cacheFile.exists()) {
          return cacheFile;
        }
      }
      // Look for file on classpath
      ClassLoader cl = getClassLoader();
      InputStream is = cl.getResourceAsStream(fileName);
      if (is != null) {
        // Copy it to cacheDir
        if (cacheFile == null) {
          setupCacheDir();
          cacheFile = new File(cacheDir, fileName);
          cacheFile.getParentFile().mkdirs();
        }
        try {
          Files.copy(is, cacheFile.toPath());
        } catch (IOException e) {
          throw new VertxException("Failed to copy file", e);
        }
        return cacheFile;
      }

    }
    return file;
  }

  private ClassLoader getClassLoader() {
    ClassLoader cl = Thread.currentThread().getContextClassLoader();
    if (cl == null) {
      cl = getClass().getClassLoader();
    }
    return cl;
  }

}
