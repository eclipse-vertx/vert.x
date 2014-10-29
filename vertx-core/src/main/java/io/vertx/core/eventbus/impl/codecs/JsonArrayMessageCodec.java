/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.eventbus.impl.codecs;

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonArray;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JsonArrayMessageCodec implements MessageCodec<JsonArray, JsonArray> {

  @Override
  public void encodeToWire(Buffer buffer, JsonArray jsonArray) {
    String strJson = jsonArray.encode();
    byte[] encoded = strJson.getBytes(CharsetUtil.UTF_8);
    buffer.appendInt(encoded.length);
    Buffer buff = Buffer.buffer(encoded);
    buffer.appendBuffer(buff);
  }

  @Override
  public JsonArray decodeFromWire(int pos, Buffer buffer) {
    int length = buffer.getInt(pos);
    pos += 4;
    byte[] encoded = buffer.getBytes(pos, pos + length);
    String str = new String(encoded, CharsetUtil.UTF_8);
    return new JsonArray(str);
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
