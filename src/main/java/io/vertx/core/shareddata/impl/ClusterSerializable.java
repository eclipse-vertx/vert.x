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

package io.vertx.core.shareddata.impl;

import io.vertx.core.buffer.Buffer;

/**
 * Objects implementing this interface will be write to and read from a {@link Buffer} when respectively
 * stored and read from an {@link io.vertx.core.shareddata.AsyncMap}.
 * <p>
 * Implementations must have a public no-argument constructor.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface ClusterSerializable {

  void writeToBuffer(Buffer buffer);

  int readFromBuffer(int pos, Buffer buffer);
}
