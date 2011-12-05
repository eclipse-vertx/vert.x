/*
 * Copyright 2010 Red Hat, Inc.
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
 */

package org.vertx.java.core.http.ws.hybi08;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;

import java.nio.ByteOrder;
import java.security.SecureRandom;

import static org.vertx.java.core.http.ws.WebSocketFrame.FrameType;

public class WebSocketFrameEncoder08 extends OneToOneEncoder {

  public WebSocketFrameEncoder08(boolean shouldMask) {
    if (shouldMask) {
      this.random = new SecureRandom();
    }
  }

  @Override
  protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
    if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;
      FrameType frameType = frame.getType();

      int opcode = encodeOpcode(frameType);

      ChannelBuffer data = frame.getBinaryData();
      int dataLen = data.readableBytes();

      ChannelBuffer encoded = ChannelBuffers.dynamicBuffer(ByteOrder.BIG_ENDIAN, data.readableBytes() + 32);

      byte firstByte = (byte) opcode;
      firstByte = (byte) (firstByte | 0x80);

      encoded.writeByte(firstByte);

      if (dataLen <= 125) {
        encoded.writeByte(applyMaskBit(dataLen));
      } else if (dataLen < 0xFFFF) {
        encoded.writeByte(applyMaskBit(0x7E));
        encoded.writeShort(dataLen);
      } else {
        encoded.writeByte(applyMaskBit(0x7F));
        encoded.writeInt(dataLen);
      }

      if (shouldMask()) {
        byte[] mask = getMask();
        encoded.writeBytes(mask);
        applyDataMask(mask, data);
      }
      encoded.writeBytes(data);

      return encoded;
    }
    return msg;
  }

  protected int encodeOpcode(FrameType frameType) {
    switch (frameType) {
      case CONTINUATION:
        return 0x0;
      case TEXT:
        return 0x1;
      case BINARY:
        return 0x2;
      case CLOSE:
        return 0x8;
      case PING:
        return 0x9;
      case PONG:
        return 0xA;
    }

    return -1;
  }

  protected byte applyMaskBit(int value) {
    if (shouldMask()) {
      return (byte) (value | 0x80);
    }
    return (byte) value;
  }

  protected void applyDataMask(byte[] mask, ChannelBuffer data) {
    if (!shouldMask()) {
      return;
    }

    int dataLen = data.readableBytes();
    data.markReaderIndex();
    for (int i = 0; i < dataLen; ++i) {
      byte cur = data.getByte(i);
      cur = (byte) (cur ^ mask[i % 4]);
      data.setByte(i, cur);
    }
    data.resetReaderIndex();
  }

  protected byte[] getMask() {
    byte[] mask = new byte[4];
    this.random.nextBytes(mask);
    return mask;
  }

  protected boolean shouldMask() {
    return (this.random != null);
  }

  private static Logger log = Logger.getLogger(WebSocketFrameEncoder08.class);
  private SecureRandom random;

}
