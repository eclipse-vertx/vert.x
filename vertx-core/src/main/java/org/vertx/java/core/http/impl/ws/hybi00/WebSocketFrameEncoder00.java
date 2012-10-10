/*
 * Copyright 2008-2011 Red Hat, Inc.
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

package org.vertx.java.core.http.impl.ws.hybi00;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import static org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketFrameEncoder00 extends OneToOneEncoder {

  private static final Logger log = LoggerFactory.getLogger(WebSocketFrameDecoder00.class);

  protected Object encode(ChannelHandlerContext ctx, Channel channel, Object msg) throws Exception {
    if (msg instanceof WebSocketFrame) {
      WebSocketFrame frame = (WebSocketFrame) msg;

      FrameType frameType = frame.getType();

      switch (frameType) {
        case CLOSE: {
          ChannelBuffer encoded = channel.getConfig().getBufferFactory().getBuffer(2);
          encoded.writeByte(0xFF);
          encoded.writeByte(0x00);
          return encoded;
        }
        case TEXT: {
          ChannelBuffer data = frame.getBinaryData();
          ChannelBuffer encoded = channel.getConfig().getBufferFactory().getBuffer(data.order(), data.readableBytes() + 2);
          encoded.writeByte(0x00);
          encoded.writeBytes(data, data.readableBytes());
          encoded.writeByte((byte) 0xFF);
          return encoded;
        }
        case BINARY: {
          ChannelBuffer data = frame.getBinaryData();
          int dataLen = data.readableBytes();
          ChannelBuffer encoded = channel.getConfig().getBufferFactory().getBuffer(data.order(), dataLen + 5);
          encoded.writeByte((byte) 0x80);
          encoded.writeByte((byte) (dataLen >>> 28 & 0x7F | 0x80));
          encoded.writeByte((byte) (dataLen >>> 14 & 0x7F | 0x80));
          encoded.writeByte((byte) (dataLen >>> 7 & 0x7F | 0x80));
          encoded.writeByte((byte) (dataLen & 0x7F));
          encoded.writeBytes(data, dataLen);
          return encoded;
        }
        default:
          break;
      }
    }
    return msg;
  }
}
