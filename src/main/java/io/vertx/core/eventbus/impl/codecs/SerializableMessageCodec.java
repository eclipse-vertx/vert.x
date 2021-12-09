/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * @author <a href="mailto:craigday3@gmail.com">Craig Day</a>
 */
public class SerializableMessageCodec<T extends Serializable> implements MessageCodec<T, T> {

  private final Class<T> clazz;

  public SerializableMessageCodec(Class<T> clazz) {
    this.clazz = clazz;
  }

  @Override
  public void encodeToWire(Buffer buffer, Serializable object) {
    try {
      ByteArrayOutputStream byteOs = new ByteArrayOutputStream();
      ObjectOutputStream oos = new ObjectOutputStream(byteOs);
      oos.writeObject(object);
      oos.flush();
      oos.close();
      byte[] bytes = byteOs.toByteArray();
      buffer.appendInt(bytes.length);
      buffer.appendBytes(bytes);
    } catch (IOException e) {
      // TODO: what should we do here?
      throw new RuntimeException(e);
    }
  }

  @Override
  public T decodeFromWire(int pos, Buffer buffer) {
    try {
      int numBytes = buffer.getInt(pos);
      pos += Integer.BYTES;
      byte[] bytes = buffer.getBytes(pos, pos + numBytes);
      ByteArrayInputStream byteIs = new ByteArrayInputStream(bytes);
      ObjectInputStream ois = new ObjectInputStream(byteIs);
      T object = clazz.cast(ois.readObject());
      ois.close();
      return object;
    } catch (IOException | ClassNotFoundException e) {
      // TODO: what should we do here?
      throw new RuntimeException(e);
    }
  }

  @Override
  public T transform(T t) {
    // TODO: This is not very performant, but it allows us to copy an object without reflection
    Buffer cloneBuf = Buffer.buffer();
    encodeToWire(cloneBuf, t);
    return decodeFromWire(0, cloneBuf);
  }

  @Override
  public String name() {
    return "serializable";
  }

  @Override
  public byte systemCodecID() {
    return 16;
  }
}
