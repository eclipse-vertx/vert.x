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

package io.vertx.core.eventbus.impl.codecs;

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.CodecManager;
import io.vertx.core.shareddata.ClusterSerializable;

import static io.vertx.core.impl.ClusterSerializableUtils.copy;

public class ClusterSerializableCodec implements MessageCodec<ClusterSerializable, ClusterSerializable> {

  private final CodecManager codecManager;

  public ClusterSerializableCodec(CodecManager codecManager) {
    this.codecManager = codecManager;
  }

  @Override
  public void encodeToWire(Buffer buffer, ClusterSerializable obj) {
    byte[] classNameBytes = obj.getClass().getName().getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(classNameBytes.length).appendBytes(classNameBytes);
    obj.writeToBuffer(buffer);
  }

  @Override
  public ClusterSerializable decodeFromWire(int pos, Buffer buffer) {
    int len = buffer.getInt(pos);
    pos += 4;
    byte[] classNameBytes = buffer.getBytes(pos, pos + len);
    String className = new String(classNameBytes, CharsetUtil.UTF_8);
    if (!codecManager.acceptClusterSerializable(className)) {
      throw new RuntimeException("Class not allowed: " + className);
    }
    pos += len;
    ClusterSerializable clusterSerializable;
    try {
      Class<?> clazz = getClassLoader().loadClass(className);
      clusterSerializable = (ClusterSerializable) clazz.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    clusterSerializable.readFromBuffer(pos, buffer);
    return clusterSerializable;
  }

  private static ClassLoader getClassLoader() {
    ClassLoader tccl = Thread.currentThread().getContextClassLoader();
    return tccl != null ? tccl : ClusterSerializableCodec.class.getClassLoader();
  }

  @Override
  public ClusterSerializable transform(ClusterSerializable obj) {
    return copy(obj);
  }

  @Override
  public String name() {
    return "clusterserializable";
  }

  @Override
  public byte systemCodecID() {
    return 16;
  }
}
