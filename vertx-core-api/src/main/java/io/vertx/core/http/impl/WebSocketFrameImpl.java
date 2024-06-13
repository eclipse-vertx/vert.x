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

package io.vertx.core.http.impl;

import io.netty.util.CharsetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.WebSocketFrameType;

import java.nio.charset.StandardCharsets;

/**
 * The default {@link WebSocketFrame} implementation.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public class WebSocketFrameImpl implements WebSocketFrame {

  private final WebSocketFrameType type;
  private final boolean isFinalFrame;
  private Buffer binaryData;

  private boolean closeParsed = false;
  private short closeStatusCode;
  private String closeReason;

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
    this.type = WebSocketFrameType.TEXT;
    this.isFinalFrame = isFinalFrame;
    this.binaryData = Buffer.buffer(textData);
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
  public WebSocketFrameImpl(WebSocketFrameType type, Buffer binaryData) {
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
  public WebSocketFrameImpl(WebSocketFrameType type, Buffer binaryData, boolean isFinalFrame) {
    this.type = type;
    this.isFinalFrame = isFinalFrame;
    this.binaryData = binaryData;
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

  public String textData() {
    return binaryData().toString(CharsetUtil.UTF_8);
  }

  public Buffer binaryData() {
    return binaryData;
  }

  @Override
  public int length() {
    return binaryData.length();
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() +
        "(type: " + type + ", " + "data: " + binaryData() + ')';
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
      closeStatusCode = binaryData.getShort(0);
      if (length == 2) {
        closeReason = null;
      } else {
        closeReason = binaryData.slice(2, length).toString(StandardCharsets.UTF_8);
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
