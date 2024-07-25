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

import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.eventbus.impl.CodecManager;

import java.io.*;

import static io.vertx.core.impl.SerializableUtils.fromBytes;
import static io.vertx.core.impl.SerializableUtils.toBytes;

public class SerializableCodec implements MessageCodec<Object, Object> {

  private final CodecManager codecManager;

  public SerializableCodec(CodecManager codecManager) {
    this.codecManager = codecManager;
  }

  @Override
  public void encodeToWire(Buffer buffer, Object o) {
    byte[] bytes = toBytes(o);
    buffer.appendInt(bytes.length);
    buffer.appendBytes(bytes);
  }

  @Override
  public Object decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] bytes = buffer.getBytes(pos, pos + length);
    return fromBytes(bytes, CheckedClassNameObjectInputStream::new);
  }

  @Override
  public Object transform(Object o) {
    return fromBytes(toBytes(o), ObjectInputStream::new);
  }

  @Override
  public String name() {
    return "serializable";
  }

  @Override
  public byte systemCodecID() {
    return 17;
  }

  private class CheckedClassNameObjectInputStream extends ObjectInputStream {
    CheckedClassNameObjectInputStream(InputStream in) throws IOException {
      super(in);
    }

    @Override
    protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
      String name = desc.getName();
      if (!codecManager.acceptSerializable(name)) {
        throw new InvalidClassException("Class not allowed: " + name);
      }
      return super.resolveClass(desc);
    }
  }
}
