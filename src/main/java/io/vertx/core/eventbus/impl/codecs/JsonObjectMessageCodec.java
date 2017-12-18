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
import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonObjectMessageCodec implements MessageCodec<JsonObject, JsonObject> {

  @Override
  public void encodeToWire(Buffer buffer, JsonObject jsonObject) {
    Buffer encoded = jsonObject.toBuffer();
    buffer.appendInt(encoded.length());
    buffer.appendBuffer(encoded);
  }

  @Override
  public JsonObject decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    return new JsonObject(buffer.slice(pos, pos + length));
  }

  @Override
  public JsonObject transform(JsonObject jsonObject) {
    return jsonObject.copy();
  }

  @Override
  public String name() {
    return "jsonobject";
  }

  @Override
  public byte systemCodecID() {
    return 13;
  }
}
