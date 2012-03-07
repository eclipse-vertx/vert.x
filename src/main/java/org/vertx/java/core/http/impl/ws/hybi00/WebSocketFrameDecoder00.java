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
import io.netty.handler.codec.frame.TooLongFrameException;
import io.netty.handler.codec.replay.ReplayingDecoder;
import io.netty.handler.codec.replay.VoidEnum;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import static org.vertx.java.core.http.impl.ws.WebSocketFrame.FrameType;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketFrameDecoder00 extends ReplayingDecoder<VoidEnum> {

  private static final Logger log = LoggerFactory.getLogger(WebSocketFrameDecoder00.class);

  public static final int DEFAULT_MAX_FRAME_SIZE = 128 * 1024;

  private final int maxFrameSize;

  WebSocketFrameDecoder00() {
    this(DEFAULT_MAX_FRAME_SIZE);
  }

  /**
   * Creates a new instance of {@code WebSocketFrameDecoder} with the specified {@code maxFrameSize}.  If the client
   * sends a frame size larger than {@code maxFrameSize}, the channel will be closed.
   *
   * @param maxFrameSize the maximum frame size to decode
   */
  WebSocketFrameDecoder00(int maxFrameSize) {
    this.maxFrameSize = maxFrameSize;
  }

  @Override
  protected Object decode(ChannelHandlerContext ctx, Channel channel,
                          ChannelBuffer buffer, VoidEnum state) throws Exception {
    byte type = buffer.readByte();

    if ((type & 0x80) == 0x80) {
      // If the MSB on type is set, decode the frame length
      return decodeBinaryFrame(type, buffer);
    } else {
      // Decode a 0xff terminated UTF-8 string
      return decodeTextFrame(type, buffer);
    }
  }

  private WebSocketFrame decodeBinaryFrame(int type, ChannelBuffer buffer) throws TooLongFrameException {
    long frameSize = 0;
    int lengthFieldSize = 0;
    byte b;
    do {
      b = buffer.readByte();
      frameSize <<= 7;
      frameSize |= b & 0x7f;
      if (frameSize > maxFrameSize) {
        throw new TooLongFrameException();
      }
      lengthFieldSize++;
      if (lengthFieldSize > 8) {
        // Perhaps a malicious peer?
        throw new TooLongFrameException();
      }
    } while ((b & 0x80) == 0x80);

    if (frameSize == 0 && type == -1) {
      return new DefaultWebSocketFrame(FrameType.CLOSE);
    }

    return new DefaultWebSocketFrame(FrameType.BINARY, buffer.readBytes((int) frameSize));
  }

  private WebSocketFrame decodeTextFrame(int type, ChannelBuffer buffer) throws TooLongFrameException {
    int ridx = buffer.readerIndex();
    int rbytes = actualReadableBytes();
    int delimPos = buffer.indexOf(ridx, ridx + rbytes, (byte) 0xFF);
    if (delimPos == -1) {
      // Frame delimiter (0xFF) not found
      if (rbytes > maxFrameSize) {
        // Frame length exceeded the maximum
        throw new TooLongFrameException();
      } else {
        // Wait until more data is received
        return null;
      }
    }

    int frameSize = delimPos - ridx;
    if (frameSize > maxFrameSize) {
      throw new TooLongFrameException();
    }

    ChannelBuffer binaryData = buffer.readBytes(frameSize);
    buffer.skipBytes(1);
    DefaultWebSocketFrame frame = new DefaultWebSocketFrame(FrameType.TEXT, binaryData);
    return frame;
  }
}
