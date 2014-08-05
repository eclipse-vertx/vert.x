/*
 * Copyright (c) 2010 The Netty Project
 * ------------------------------------
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
 */

package io.vertx.core.http.impl.ws;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCounted;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.FrameType;

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
  public boolean isFinal() {
    return isFinalFrame;
  }

  @Override
  public FrameType type() {
    return type;
  }
}
