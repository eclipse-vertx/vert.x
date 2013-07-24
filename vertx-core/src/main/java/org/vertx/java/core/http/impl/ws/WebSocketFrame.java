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

/**
 * A Web Socket frame that represents either text or binary data.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface WebSocketFrame {

  /**
   * List of all frame types.
   */
  enum FrameType {
    CONTINUATION,
    TEXT,
    BINARY,
    CLOSE,
    PING,
    PONG,
  }

  FrameType getType();

  /**
   * Returns {@code true} if and only if the content of this frame is a string
   * encoded in UTF-8.
   */
  boolean isText();

  /**
   * Returns {@code true} if and only if the content of this frame is an
   * arbitrary binary data.
   */
  boolean isBinary();

  /**
   * Returns the content of this frame as-is, with no UTF-8 decoding.
   */
  ByteBuf getBinaryData();

  /**
   * Converts the content of this frame into a UTF-8 string and returns the
   * converted string.
   */
  String getTextData();

  /**
   * Sets the type and the content of this frame.
   *
   * @param binaryData the content of the frame.  If <tt>(type &amp; 0x80 == 0)</tt>,
   *                   it must be encoded in UTF-8.
   * @throws IllegalArgumentException if If <tt>(type &amp; 0x80 == 0)</tt> and the data is not encoded
   *                                  in UTF-8
   */
  void setBinaryData(ByteBuf binaryData);

  /**
   * Set the type of the content of this frame and populate it with the given content
   *
   * @param textData the content of the frame. Must be valid UTF-8
   */
  void setTextData(String textData);

  /**
   * Returns the string representation of this frame.  Please note that this
   * method is not identical to {@link #getTextData()}.
   */
  String toString();
}
