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
import io.vertx.core.json.JsonArray;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArrayMessageCodec implements MessageCodec<JsonArray, JsonArray> {

  @Override
  public void encodeToWire(Buffer buffer, JsonArray jsonArray) {
    Buffer encoded = jsonArray.toBuffer();
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
  }

  @Override
  public JsonArray decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    return new JsonArray(buffer.slice(pos, pos + length));
  }

  @Override
  public JsonArray transform(JsonArray jsonArray) {
    return jsonArray.copy();
  }

  @Override
  public String name() {
    return "jsonarray";
  }

  @Override
  public byte systemCodecID() {
    return 14;
  }
}
