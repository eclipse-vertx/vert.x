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

package org.vertx.java.core.http.impl.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;

/**
 * The default {@link WebSocketFrame} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class DefaultWebSocketFrame implements WebSocketFrame, ReferenceCounted {

  private final FrameType type;
  private ByteBuf binaryData;

  /**
   * Creates a new empty text frame.
   */
  public DefaultWebSocketFrame() {
    this(null, Unpooled.EMPTY_BUFFER);
  }

  public DefaultWebSocketFrame(FrameType frameType) {
    this(frameType, Unpooled.EMPTY_BUFFER);
  }

  /**
   * Creates a new text frame from with the specified string.
   */
  public DefaultWebSocketFrame(String textData) {
    this(FrameType.TEXT, Unpooled.copiedBuffer(textData, CharsetUtil.UTF_8));
  }

  /**
   * Creates a new frame with the specified frame type and the specified data.
   *
   * @param type       the type of the frame. {@code 0} is the only allowed type currently.
   * @param binaryData the content of the frame.  If <tt>(type &amp; 0x80 == 0)</tt>,
   *                   it must be encoded in UTF-8.
   * @throws IllegalArgumentException if If <tt>(type &amp; 0x80 == 0)</tt> and the data is not encoded
   *                                  in UTF-8
   */
  public DefaultWebSocketFrame(FrameType type, ByteBuf binaryData) {
    this.type = type;
    this.binaryData = Unpooled.unreleasableBuffer(binaryData);
  }

  public FrameType getType() {
    return type;
  }

  public boolean isText() {
    return this.type == FrameType.TEXT;
  }

  public boolean isBinary() {
    return this.type == FrameType.BINARY;
  }

  public ByteBuf getBinaryData() {
    return binaryData;
  }

  public String getTextData() {
    return getBinaryData().toString(CharsetUtil.UTF_8);
  }

  public void setBinaryData(ByteBuf binaryData) {
    if (this.binaryData != null) {
      this.binaryData.release();
    }
    this.binaryData = binaryData;
  }

  public void setTextData(String textData) {
    if (this.binaryData != null) {
      this.binaryData.release();
    }
    this.binaryData = Unpooled.copiedBuffer(textData, CharsetUtil.UTF_8);
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() +
        "(type: " + getType() + ", " + "data: " + getBinaryData() + ')';
  }

  @Override
  public int refCnt() {
    return binaryData.refCnt();
  }

  @Override
  public ReferenceCounted retain() {
    return binaryData.retain();
  }

  @Override
  public ReferenceCounted retain(int increment) {
    return binaryData.retain(increment);
  }

  @Override
  public boolean release() {
    return binaryData.release();
  }

  @Override
  public boolean release(int decrement) {
    return binaryData.release(decrement);
  }
}
