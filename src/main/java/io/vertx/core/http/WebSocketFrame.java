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

package io.vertx.core.http;

import io.vertx.codegen.annotations.CacheReturn;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.ServiceHelper;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.spi.WebSocketFrameFactory;

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
@VertxGen
public interface WebSocketFrame {

  /**
   * Create a binary WebSocket frame.
   *
   * @param data  the data for the frame
   * @param isFinal  true if it's the final frame in the WebSocket message
   * @return the frame
   */
  static WebSocketFrame binaryFrame(Buffer data, boolean isFinal) {
    return factory.binaryFrame(data, isFinal);
  }

  /**
   * Create a text WebSocket frame.
   *
   * @param str  the string for the frame
   * @param isFinal  true if it's the final frame in the WebSocket message
   * @return the frame
   */
  static WebSocketFrame textFrame(String str, boolean isFinal) {
    return factory.textFrame(str, isFinal);
  }

  /**
   * Create a ping WebSocket frame.  Will be a final frame. There is no option for non final ping frames.
   *
   * @param data the bytes for the frame, may be at most 125 bytes
   * @return the frame
   */
  static WebSocketFrame pingFrame(Buffer data) {
    return factory.pingFrame(data);
  }

  /**
   * Create a pong WebSocket frame.  Will be a final frame. There is no option for non final pong frames.
   *
   * @param data the bytes for the frame, may be at most 125 bytes
   * @return the frame
   */
  static WebSocketFrame pongFrame(Buffer data) {
    return factory.pongFrame(data);
  }

  /**
   * Create a continuation frame
   *
   * @param data  the data for the frame
   * @param isFinal true if it's the final frame in the WebSocket message
   * @return the frame
   */
  static WebSocketFrame continuationFrame(Buffer data, boolean isFinal) {
    return factory.continuationFrame(data, isFinal);
  }

  /**
   * @return true if it's a text frame
   */
  boolean isText();

  /**
   * @return true if it's a binary frame
   */
  boolean isBinary();

  /**
   * @return true if it's a continuation frame
   */
  boolean isContinuation();

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

  WebSocketFrameFactory factory = ServiceHelper.loadFactory(WebSocketFrameFactory.class);
}
