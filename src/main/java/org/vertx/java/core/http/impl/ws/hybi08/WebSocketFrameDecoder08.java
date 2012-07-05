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
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.impl.ws.hybi08;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.frame.TooLongFrameException;
import org.jboss.netty.handler.codec.replay.ReplayingDecoder;
import org.jboss.netty.handler.codec.replay.VoidEnum;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import static org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType;

public class WebSocketFrameDecoder08 extends ReplayingDecoder<VoidEnum> {

  public static final int DEFAULT_MAX_FRAME_SIZE = 128 * 1024;

  private final int maxFrameSize;

  WebSocketFrameDecoder08() {
    this(DEFAULT_MAX_FRAME_SIZE);
  }

  /**
   * Creates a new instance of {@code WebSocketFrameDecoder} with the
   * specified {@code maxFrameSize}. If the client
   * sends a frame size larger than {@code maxFrameSize}, the channel will be
   * closed.
   *
   * @param maxFrameSize the maximum frame size to decode
   */
  WebSocketFrameDecoder08(int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
  }


  @Override
  protected Object decode(ChannelHandlerContext ctx, Channel channel, ChannelBuffer buffer, VoidEnum state) throws Exception {

    int finOpcode = buffer.readUnsignedByte();

    boolean fin = ((finOpcode & 0x80) != 0);
    int opcode = (finOpcode & 0x0F);

    byte lengthMask = buffer.readByte();

    boolean masked = ((lengthMask & 0x80) != 0);

    long length = (lengthMask & 0x7F);

    if (length == 126) {
      length = buffer.readUnsignedShort();
    } else if (length == 127) {
      length = buffer.readLong();
    }

    if (length > this.maxFrameSize) {
      throw new TooLongFrameException();
    }

    byte[] mask = null;
    if (masked) {
      mask = new byte[4];
      buffer.readBytes(mask);
    }

    byte[] body = new byte[(int) length];

    buffer.readBytes(body);
    if (masked) {
      for (int i = 0; i < body.length; ++i) {
        body[i] = (byte) (body[i] ^ mask[i % 4]);
      }
    }

    ChannelBuffer data = ChannelBuffers.wrappedBuffer(body);

    FrameType frameType = decodeFrameType(opcode);

    return new DefaultWebSocketFrame(frameType, data);
  }

  protected FrameType decodeFrameType(int opcode) {
    switch (opcode) {
      case 0x0:
        return FrameType.CONTINUATION;
      case 0x1:
        return FrameType.TEXT;
      case 0x2:
        return FrameType.BINARY;
      case 0x8:
        return FrameType.CLOSE;
      case 0x9:
        return FrameType.PING;
      case 0xA:
        return FrameType.PONG;
    }

    return null;
  }

  private static final Logger log = LoggerFactory.getLogger(WebSocketFrameDecoder08.class);

}
