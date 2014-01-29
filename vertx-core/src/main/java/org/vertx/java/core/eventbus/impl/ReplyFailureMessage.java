/*
 * Copyright 2013 Red Hat, Inc.
 *
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
 *
 */

package org.vertx.java.core.eventbus.impl;

import io.netty.util.CharsetUtil;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;


/**
 * Special message that transmits a send reply failure to a send handler
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReplyFailureMessage extends BaseMessage<ReplyException> {

  ReplyFailureMessage(String address, ReplyException body) {
    super(true, address, body);
  }

  public ReplyFailureMessage(Buffer readBuff) {
    super(readBuff);
  }

  private byte[] encoded;

  @Override
  protected void readBody(int pos, Buffer readBuff) {
    int i = (int)readBuff.getByte(pos);
    ReplyFailure rf = ReplyFailure.fromInt(i);
    pos++;
    int failureCode = readBuff.getInt(pos);
    pos += 4;
    boolean isNull = readBuff.getByte(pos) == (byte)0;
    String message;
    if (!isNull) {
      pos++;
      int strLength = readBuff.getInt(pos);
      pos += 4;
      byte[] bytes = readBuff.getBytes(pos, pos + strLength);
      message = new String(bytes, CharsetUtil.UTF_8);
    } else {
      message = null;
    }
    body = new ReplyException(rf, failureCode, message);
  }

  @Override
  protected void writeBody(Buffer buff) {
    buff.appendByte((byte)body.failureType().toInt());
    buff.appendInt(body.failureCode());
    if (body.getMessage() == null) {
      buff.appendByte((byte)0);
    } else {
      buff.appendByte((byte)1);
      buff.appendInt(encoded.length);
      buff.appendBytes(encoded);
    }
  }

  @Override
  protected int getBodyLength() {
    if (body.getMessage() == null) {
      return 1 + 4 + 1;
    } else {
      encoded = body.getMessage().getBytes(CharsetUtil.UTF_8);
      return 1 + 4  + 1 + 4 + encoded.length;
    }
  }

  @Override
  protected Message<ReplyException> copy() {
    // No need to copy since everything is immutable
    return this;
  }

  @Override
  protected byte type() {
    return MessageFactory.TYPE_REPLY_FAILURE;
  }

}
