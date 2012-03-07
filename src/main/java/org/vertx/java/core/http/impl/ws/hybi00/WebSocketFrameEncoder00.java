/*
 * Copyright 2008-2011 Red Hat, Inc, and individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 * Modified from original form by Tim Fox
 */

package org.vertx.java.core.http.impl.ws.hybi00;

import io.netty.buffer.ChannelBuffer;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.oneone.OneToOneEncoder;
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
