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

package io.vertx.core.http;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;

/**
 * A WebSocket frame that represents either text or binary data.
 * <p>
 * A WebSocket message is composed of one or more WebSocket frames.
 * <p>
 * If there is a just a single frame in the message then a single text or binary frame should be created with final = true.
 * <p>
 * If there are more than one frames in the message, then the first frame should be a text or binary frame with
 * final = false, followed by one or more continuation frames. The last continuation frame should have final = true.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
@DataObject
public interface WebSocketFrame {

  /**
   * Create a binary WebSocket frame.
   *
   * @param data  the data for the frame
   * @param isFinal  true if it's the final frame in the WebSocket message
   * @return the frame
   */
  static WebSocketFrame binaryFrame(Buffer data, boolean isFinal) {
    return WebSocketFrameImpl.binaryFrame(data, isFinal);
  }

  /**
   * Create a text WebSocket frame.
   *
   * @param str  the string for the frame
   * @param isFinal  true if it's the final frame in the WebSocket message
   * @return the frame
   */
  static WebSocketFrame textFrame(String str, boolean isFinal) {
    return WebSocketFrameImpl.textFrame(str, isFinal);
  }

  /**
   * Create a ping WebSocket frame.  Will be a final frame. There is no option for non final ping frames.
   *
   * @param data the bytes for the frame, may be at most 125 bytes
   * @return the frame
   */
  static WebSocketFrame pingFrame(Buffer data) {
    return WebSocketFrameImpl.pingFrame(data);
  }

  /**
   * Create a pong WebSocket frame.  Will be a final frame. There is no option for non final pong frames.
   *
   * @param data the bytes for the frame, may be at most 125 bytes
   * @return the frame
   */
  static WebSocketFrame pongFrame(Buffer data) {
    return WebSocketFrameImpl.pongFrame(data);
  }

  /**
   * Create a continuation frame
   *
   * @param data  the data for the frame
   * @param isFinal true if it's the final frame in the WebSocket message
   * @return the frame
   */
  static WebSocketFrame continuationFrame(Buffer data, boolean isFinal) {
    return WebSocketFrameImpl.continuationFrame(data, isFinal);
  }

  /**
   * @return the frame type
   */
  WebSocketFrameType type();

  /**
   * @return whether the frame is a {@link WebSocketFrameType#TEXT} frame
   */
  boolean isText();

  /**
   * @return whether the frame is a {@link WebSocketFrameType#BINARY} frame
   */
  boolean isBinary();

  /**
   * @return whether the frame is a {@link WebSocketFrameType#CONTINUATION} frame
   */
  boolean isContinuation();

  /**
   * @return whether the frame is a {@link WebSocketFrameType#CLOSE} frame
   */
  boolean isClose();

  /**
   * @return whether the frame is a {@link WebSocketFrameType#PING} frame
   */
  boolean isPing();

  /**
   * @return the content of this frame as a UTF-8 string and returns the
   * converted string. Only use this for text frames.
   */
  @CacheReturn
  String textData();

  /**
   * @return the data of the frame
   */
  @CacheReturn
  Buffer binaryData();

  /**
   * @return true if this is the final frame.
   */
  boolean isFinal();

  /**
   * @return status code of close frame. Only use this for close frames
   */
  short closeStatusCode();

  /**
   * @return string explaining close reason. Only use this for close frames
   */
  String closeReason();

}
