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

package io.vertx.core.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.ClusterSerializable;

import java.lang.reflect.InvocationTargetException;

public class ClusterSerializableUtils {

  public static ClusterSerializable copy(ClusterSerializable obj) {
    Buffer buffer = Buffer.buffer();
    obj.writeToBuffer(buffer);
    try {
      ClusterSerializable copy = obj.getClass().getConstructor().newInstance();
      copy.readFromBuffer(0, buffer);
      return copy;
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
      throw new RuntimeException(e);
    }
  }

  private ClusterSerializableUtils() {
    // Utility class
  }
}
