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
 * Objects implementing this interface will be written to and read from a {@link Buffer} when:
 * <ul>
 *   <li>stored in and read from an {@link AsyncMap}, or</li>
 *   <li>encodec to and decoded from an {@link io.vertx.core.eventbus.EventBus} message body</li>
 * </ul>
 *
 * @implSpec Implementations must have a public no-argument constructor.
 */
public interface ClusterSerializable {

  /**
   * Method invoked when serializing this instance.
   *
   * @param buffer the {@link Buffer} where the serialized bytes must be written to
   */
  void writeToBuffer(Buffer buffer);

  /**
   * Method invoked when deserializing bytes to this instance.
   *
   * @param pos the position where to start reading the {@code buffer}
   * @param buffer the {@link Buffer} where the serialized bytes must be read from
   * @return the position after the last serialized byte
   */
  int readFromBuffer(int pos, Buffer buffer);
}
