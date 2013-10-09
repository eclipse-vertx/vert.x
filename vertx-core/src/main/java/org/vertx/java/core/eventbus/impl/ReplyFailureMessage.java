/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
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
