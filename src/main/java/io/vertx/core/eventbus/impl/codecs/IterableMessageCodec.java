/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * @author Josef Pfleger
 */
public class IterableMessageCodec implements MessageCodec<Iterable, Iterable> {

  private final CodecManager codecs;

  public IterableMessageCodec(CodecManager codecs) {
    this.codecs = codecs;
  }

  @Override
  public void encodeToWire(Buffer buffer, Iterable iterable) {
    List l = new ArrayList<>();
    iterable.forEach(l::add);
    buffer.appendInt(l.size());
    if (l.size() < 1) {
      return;
    }
    MessageCodec codec = codecs.lookupCodec(l.get(0), null);
    buffer.appendByte(codec.systemCodecID());
    for (Object i : l) {
      Buffer b = Buffer.buffer();
      codec.encodeToWire(b, i);
      buffer.appendInt(b.length());
      buffer.appendBuffer(b);
    }
  }

  @Override
  public Iterable decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    List<Object> l = new ArrayList<>();
    if (length < 1) {
      return l;
    }
    MessageCodec codec = codecs.systemCodecs()[buffer.getByte(pos++)];
    for (int i = 0; i < length; i++) {
      int size = buffer.getInt(pos);
      pos += 4;
      l.add(codec.decodeFromWire(pos, buffer));
      pos += size;
    }
    return l;
  }

  @Override
  public Iterable transform(Iterable iterable) {
    Iterator it = iterable.iterator();
    if (!it.hasNext()) {
      return new ArrayList();
    }
    Object first = it.next();
    MessageCodec codec = codecs.lookupCodec(first, null);
    List l = new ArrayList();
    l.add(codec.transform(first));
    it.forEachRemaining(i -> l.add(codec.transform(i)));
    return l;
  }

  @Override
  public String name() {
    return "iterable";
  }

  @Override
  public byte systemCodecID() {
    return 16;
  }
}
