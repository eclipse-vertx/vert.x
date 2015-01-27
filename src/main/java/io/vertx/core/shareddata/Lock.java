/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
