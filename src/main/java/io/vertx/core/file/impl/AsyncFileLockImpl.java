/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.file.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.file.AsyncFileLock;
import io.vertx.core.file.FileSystemException;
import io.vertx.core.impl.VertxInternal;

import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Objects;

public class AsyncFileLockImpl implements AsyncFileLock {

  private final VertxInternal vertx;
  private final FileLock fileLock;

  public AsyncFileLockImpl(VertxInternal vertx, FileLock fileLock) {
    this.vertx = Objects.requireNonNull(vertx, "vertx is null");
    this.fileLock = Objects.requireNonNull(fileLock, "fileLock is null");
  }

  @Override
  public long position() {
    return fileLock.position();
  }

  @Override
  public long size() {
    return fileLock.size();
  }

  @Override
  public boolean isShared() {
    return fileLock.isShared();
  }

  @Override
  public boolean overlaps(long position, long size) {
    return fileLock.overlaps(position, size);
  }

  @Override
  public boolean isValidBlocking() {
    return fileLock.isValid();
  }

  @Override
  public Future<Boolean> isValid() {
    return vertx.getOrCreateContext().executeBlockingInternal(prom -> {
      prom.complete(isValidBlocking());
    });
  }

  @Override
  public void releaseBlocking() {
    try {
      fileLock.release();
    } catch (IOException e) {
      throw new FileSystemException(e);
    }
  }

  @Override
  public Future<Void> release() {
    return vertx.getOrCreateContext().executeBlockingInternal(prom -> {
      try {
        fileLock.release();
        prom.complete();
      } catch (IOException e) {
        prom.fail(new FileSystemException(e));
      }
    });
  }
}
