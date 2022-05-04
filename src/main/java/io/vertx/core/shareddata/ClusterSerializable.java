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

package io.vertx.core.shareddata;

import io.vertx.core.buffer.Buffer;

/**
 * Objects implementing this interface will be written to and read from a {@link Buffer} when respectively
 * stored and read from an {@link AsyncMap}.
 *
 * @implSpec Implementations must have a public no-argument constructor.
 */
public interface ClusterSerializable extends io.vertx.core.shareddata.impl.ClusterSerializable {

  @Override
  void writeToBuffer(Buffer buffer);

  @Override
  int readFromBuffer(int pos, Buffer buffer);
}
