/*
 * Copyright (c) 2010 The Netty Project
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
import io.vertx.core.http.impl.FrameType;

import java.nio.charset.StandardCharsets;

/**
 * The default {@link WebSocketFrameInternal} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class WebSocketFrameImpl implements WebSocketFrameInternal, ReferenceCounted {


  private final FrameType type;
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
  public WebSocketFrameImpl(FrameType frameType) {
    this(frameType, Unpooled.EMPTY_BUFFER, true);
  }

  /**
   * Creates a new text frame from with the specified string.
   */
  public WebSocketFrameImpl(String textData) {
    this(textData, true);
  }

  /**
   * Creates a new text frame from with the specified string.
   */
  public WebSocketFrameImpl(String textData, boolean isFinalFrame) {
    this.type = FrameType.TEXT;
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
  public WebSocketFrameImpl(FrameType type, ByteBuf binaryData) {
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
  public WebSocketFrameImpl(FrameType type, ByteBuf binaryData, boolean isFinalFrame) {
    this.type = type;
    this.isFinalFrame = isFinalFrame;
    this.binaryData = Unpooled.unreleasableBuffer(binaryData);
  }

  public boolean isText() {
    return this.type == FrameType.TEXT;
  }

  public boolean isBinary() {
    return this.type == FrameType.BINARY;
  }

  public boolean isContinuation() {
    return this.type == FrameType.CONTINUATION;
  }

  public boolean isClose() { return this.type == FrameType.CLOSE; }

  public ByteBuf getBinaryData() {
    return binaryData;
  }

  public String textData() {
    return getBinaryData().toString(CharsetUtil.UTF_8);
  }

  public Buffer binaryData() {
    return Buffer.buffer(binaryData);
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
  public FrameType type() {
    return type;
  }
}
