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
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReplyExceptionMessageCodec implements MessageCodec<ReplyException, ReplyException> {

  @Override
  public void encodeToWire(Buffer buffer, ReplyException body) {
    buffer.appendByte((byte)body.failureType().toInt());
    buffer.appendInt(body.failureCode());
    if (body.getMessage() == null) {
      buffer.appendByte((byte)0);
    } else {
      buffer.appendByte((byte)1);
      byte[] encoded = body.getMessage().getBytes(CharsetUtil.UTF_8);
      buffer.appendInt(encoded.length);
      buffer.appendBytes(encoded);
    }
  }

  @Override
  public ReplyException decodeFromWire(int pos, Buffer buffer) {
    int i = (int) buffer.getByte(pos);
    ReplyFailure rf = ReplyFailure.fromInt(i);
    pos++;
    int failureCode = buffer.getInt(pos);
    pos += 4;
    boolean isNull = buffer.getByte(pos) == (byte)0;
    String message;
    if (!isNull) {
      pos++;
      int strLength = buffer.getInt(pos);
      pos += 4;
      byte[] bytes = buffer.getBytes(pos, pos + strLength);
      message = new String(bytes, CharsetUtil.UTF_8);
    } else {
      message = null;
    }
    return new ReplyException(rf, failureCode, message);
  }

  @Override
  public ReplyException transform(ReplyException exception) {
    return exception;
  }

  @Override
  public String name() {
    return "replyexception";
  }

  @Override
  public byte systemCodecID() {
    return 15;
  }
}
