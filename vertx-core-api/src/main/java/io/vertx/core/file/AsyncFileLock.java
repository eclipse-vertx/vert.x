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

package io.vertx.core.file;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

/**
 * A lock on a region of an {@link AsyncFile}.
 */
@VertxGen
public interface AsyncFileLock {

  /**
   * @return the position of the first byte of the locked region
   */
  long position();

  /**
   * @return the size in bytes of the locked region
   */
  long size();

  /**
   * @return {@code true} if this lock is shared, otherwise {@code false}
   */
  boolean isShared();

  /**
   * @return {@code true} if this lock overlaps with the range described by {@code position} and {@code size}, otherwise {@code false}
   */
  boolean overlaps(long position, long size);

  /**
   * Like {@link #isValid()} but blocking.
   *
   * @throws FileSystemException if an error occurs
   */
  boolean isValidBlocking();

  /**
   * A lock remains valid until it is released or the file corresponding {@link AsyncFile} is closed.
   */
  Future<Boolean> isValid();

  /**
   * Like {@link #release()} but blocking.
   *
   * @throws FileSystemException if an error occurs
   */
  void releaseBlocking();

  /**
   * Releases this lock;
   */
  Future<Void> release();

}
