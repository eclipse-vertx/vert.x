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

package io.vertx.core;

/**
 * A closeable resource.
 * <p/>
 * This interface is mostly used for internal resource management of Vert.x.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface Closeable {

  /**
   * Close this resource, the {@code completion} promise must be notified when the operation has completed.
   *
   * @param completion the promise to signal when close has completed
   */
  // SHOULD BE COMPLETABLE HERE
  void close(Promise<Void> completion);
}
