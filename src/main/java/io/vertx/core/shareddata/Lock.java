/*
 * Copyright (c) 2014 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata;

import io.vertx.codegen.annotations.VertxGen;

/**
 * An asynchronous exclusive lock which can be obtained from any node in the cluster.
 * <p>
 * When the lock is obtained, no-one else in the cluster can obtain the lock with the same name until the lock
 * is released.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface Lock {

  /**
   * Release the lock. Once the lock is released another will be able to obtain the lock.
   */
  void release();
}
