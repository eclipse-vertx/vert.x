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
package io.vertx.core.file.impl;

import io.vertx.core.impl.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import java.util.UUID;

class FileCache {

  static FileCache setupCache(String fileCacheDir) {
    FileCache cache = new FileCache(setupCacheDir(fileCacheDir));
    // Add shutdown hook to delete on exit
    cache.registerShutdownHook();
    return cache;
  }

  /**
   * Prepares the cache directory to be used in the application.
   */
  static File setupCacheDir(String fileCacheDir) {
    // ensure that the argument doesn't end with separator
    if (fileCacheDir.endsWith(File.separator)) {
      fileCacheDir = fileCacheDir.substring(0, fileCacheDir.length() - File.separator.length());
    }

    // the cacheDir will be suffixed a unique id to avoid eavesdropping from other processes/users
    // also this ensures that if process A deletes cacheDir, it won't affect process B
    String cacheDirName = fileCacheDir + "-" + UUID.randomUUID().toString();
    File cacheDir = new File(cacheDirName);
    // Create the cache directory
    try {
      if (Utils.isWindows()) {
        Files.createDirectories(cacheDir.toPath());
      } else {
        // for security reasons, cache directory should not be readable/writable from other users
        // just like "POSIX mkdtemp(3)", the created directory should have 0700 permission
        Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rwx------");
        Files.createDirectories(cacheDir.toPath(), PosixFilePermissions.asFileAttribute(perms));
      }
    } catch (IOException e) {
      throw new IllegalStateException("Failed to create cache dir: " + cacheDirName, e);
    }
    return cacheDir;
  }

  private Thread shutdownHook;
  private File cacheDir;

  public FileCache(File cacheDir) {
    this.cacheDir = cacheDir;
  }

  synchronized void registerShutdownHook() {
    Thread shutdownHook = new Thread(this::runHook);
    this.shutdownHook = shutdownHook;
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  private void runHook() {
    synchronized (this) {
      // no-op if cache dir has been set to null
      if (cacheDir == null) {
        return;
      }
    }
    Thread deleteCacheDirThread = new Thread(() -> {
      try {
        deleteCacheDir();
      } catch (IOException ignore) {
      }
    });
    // start the thread
    deleteCacheDirThread.start();
    try {
      deleteCacheDirThread.join(10 * 1000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  void close() throws IOException {
    final Thread hook;
    synchronized (this) {
      hook = shutdownHook;
      // disable the shutdown hook thread
      shutdownHook = null;
    }
    if (hook != null) {
      // May throw IllegalStateException if called from other shutdown hook so ignore that
      try {
        Runtime.getRuntime().removeShutdownHook(hook);
      } catch (IllegalStateException ignore) {
        // ignore
      }
    }
    deleteCacheDir();
  }

  private void deleteCacheDir() throws IOException {
    final File dir;
    synchronized (this) {
      if (cacheDir == null) {
        return;
      }
      // save the state before we force a flip
      dir = cacheDir;
      // disable the cache dir
      cacheDir = null;
    }
    // threads will only enter here once, as the resolving flag is flipped above
    if (dir.exists()) {
      FileSystemImpl.delete(dir.toPath(), true);
    }
  }

  File getFile(String fileName) {
    // if cacheDir is null, the delete cache dir was already called.
    // only in this case the resolver is working in an unexpected state
    if (cacheDir == null) {
      throw new IllegalStateException("cacheDir is null");
    }
    return new File(cacheDir, fileName);
  }

  File cache(String fileName, File resource, boolean overwrite) throws IOException {
    File cacheFile = new File(cacheDir, fileName);
    boolean isDirectory = resource.isDirectory();
    if (!isDirectory) {
      cacheFile.getParentFile().mkdirs();
      if (!overwrite) {
        try {
          Files.copy(resource.toPath(), cacheFile.toPath());
        } catch (FileAlreadyExistsException ignore) {
        }
      } else {
        Files.copy(resource.toPath(), cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } else {
      cacheFile.mkdirs();
    }
    return cacheFile;
  }

  void cacheFile(String fileName, InputStream is, boolean overwrite) throws IOException {
    File cacheFile = new File(cacheDir, fileName);
    cacheFile.getParentFile().mkdirs();
    if (!overwrite) {
      try {
        Files.copy(is, cacheFile.toPath());
      } catch (FileAlreadyExistsException ignore) {
      }
    } else {
      Files.copy(is, cacheFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
  }

  void cacheDir(String fileName) {
    File file = new File(cacheDir, fileName);
    file.mkdirs();
  }
}
