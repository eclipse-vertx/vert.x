/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.WebSocketFrameType;

import java.nio.charset.StandardCharsets;

/**
 * The default {@link WebSocketFrameInternal} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class WebSocketFrameImpl implements WebSocketFrameInternal, ReferenceCounted {

  public static WebSocketFrame binaryFrame(Buffer data, boolean isFinal) {
    return new WebSocketFrameImpl(WebSocketFrameType.BINARY, ((BufferInternal)data).getByteBuf(), isFinal);
  }

  public static WebSocketFrame textFrame(String str, boolean isFinal) {
    return new WebSocketFrameImpl(str, isFinal);
  }

  public static WebSocketFrame continuationFrame(Buffer data, boolean isFinal) {
    return new WebSocketFrameImpl(WebSocketFrameType.CONTINUATION, ((BufferInternal)data).getByteBuf(), isFinal);
  }

  public static WebSocketFrame pingFrame(Buffer data) {
    return new WebSocketFrameImpl(WebSocketFrameType.PING, ((BufferInternal)data).getByteBuf(), true);
  }

  public static WebSocketFrame pongFrame(Buffer data) {
    return new WebSocketFrameImpl(WebSocketFrameType.PONG, ((BufferInternal)data).getByteBuf(), true);
  }

  private final WebSocketFrameType type;
  private final boolean isFinalFrame;
  private ByteBuf binaryData;

  private boolean closeParsed = false;
  private short closeStatusCode;
  private String closeReason;

  /**
   * Creates a new empty text frame.
   */
  public WebSocketFrameImpl() {
    this(null, Unpooled.EMPTY_BUFFER, true);
  }

  /**
   * Creates a new empty text frame.
   */
  public WebSocketFrameImpl(WebSocketFrameType frameType) {
    this(frameType, Unpooled.EMPTY_BUFFER, true);
  }

  /**
   * Creates a new text frame from with the specified string.
   */
  public WebSocketFrameImpl(String textData) {
    this(textData, true);
  }

  /**
   * Creates a new raw frame from with the specified encoded content.
   */
  public WebSocketFrameImpl(WebSocketFrameType type, byte[] utf8TextData, boolean isFinalFrame) {
    this.type = type;
    this.isFinalFrame = isFinalFrame;
    this.binaryData = Unpooled.wrappedBuffer(utf8TextData);
  }

  /**
   * Creates a new text frame from with the specified string.
   */
  public WebSocketFrameImpl(String textData, boolean isFinalFrame) {
    this.type = WebSocketFrameType.TEXT;
    this.isFinalFrame = isFinalFrame;
    this.binaryData = Unpooled.copiedBuffer(textData, CharsetUtil.UTF_8);
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
  public WebSocketFrameImpl(WebSocketFrameType type, ByteBuf binaryData) {
    this(type, binaryData, true);
  }

  /**
   * Creates a new frame with the specified frame type and the specified data.
   *
   * @param type       the type of the frame. {@code 0} is the only allowed type currently.
   * @param binaryData the content of the frame.  If <tt>(type &amp; 0x80 == 0)</tt>,
   *                   it must be encoded in UTF-8.
   * @param isFinalFrame If this is the final frame in a sequence
   * @throws IllegalArgumentException if If <tt>(type &amp; 0x80 == 0)</tt> and the data is not encoded
   *                                  in UTF-8
   */
  public WebSocketFrameImpl(WebSocketFrameType type, ByteBuf binaryData, boolean isFinalFrame) {
    this.type = type;
    this.isFinalFrame = isFinalFrame;
    this.binaryData = Unpooled.unreleasableBuffer(binaryData);
  }

  public boolean isText() {
    return this.type == WebSocketFrameType.TEXT;
  }

  public boolean isBinary() {
    return this.type == WebSocketFrameType.BINARY;
  }

  public boolean isContinuation() {
    return this.type == WebSocketFrameType.CONTINUATION;
  }

  public boolean isClose() { return this.type == WebSocketFrameType.CLOSE; }

  @Override
  public boolean isPing() {
    return this.type == WebSocketFrameType.PING;
  }

  public ByteBuf getBinaryData() {
    return binaryData;
  }

  public String textData() {
    return getBinaryData().toString(CharsetUtil.UTF_8);
  }

  public Buffer binaryData() {
    return BufferInternal.buffer(binaryData);
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
  public int length() {
    return binaryData.readableBytes();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() +
        "(type: " + type + ", " + "data: " + getBinaryData() + ')';
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

  @Override
  public ReferenceCounted touch() {
    binaryData.touch();
    return this;
  }

  @Override
  public ReferenceCounted touch(Object hint) {
    binaryData.touch(hint);
    return this;
  }

  @Override
  public boolean isFinal() {
    return isFinalFrame;
  }

  private void parseCloseFrame() {
    int length = length();
    if (length < 2) {
      closeStatusCode = 1000;
      closeReason = null;
    } else {
      int index = binaryData.readerIndex();
      closeStatusCode = binaryData.getShort(index);
      if (length == 2) {
        closeReason = null;
      } else {
        closeReason = binaryData.toString(index + 2, length - 2, StandardCharsets.UTF_8);
      }
    }
  }

  private void checkClose() {
    if (!isClose())
      throw new IllegalStateException("This should be a close frame");
  }

  @Override
  public short closeStatusCode() {
    checkClose();
    if (!closeParsed)
      parseCloseFrame();
    return this.closeStatusCode;
  }

  @Override
  public String closeReason() {
    checkClose();
    if (!closeParsed)
      parseCloseFrame();
    return this.closeReason;
  }

  @Override
  public WebSocketFrameType type() {
    return type;
  }
}
