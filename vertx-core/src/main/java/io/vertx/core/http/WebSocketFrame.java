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
 * A Web Socket frame that represents either text or binary data.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
@VertxGen
public interface WebSocketFrame {

  static WebSocketFrame binaryFrame(Buffer data, boolean isFinal) {
    return factory.binaryFrame(data, isFinal);
  }

  static WebSocketFrame textFrame(String str, boolean isFinal) {
    return factory.textFrame(str, isFinal);
  }

  static WebSocketFrame continuationFrame(Buffer data, boolean isFinal) {
    return factory.continuationFrame(data, isFinal);
  }

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

  boolean isContinuation();

  /**
   * Converts the content of this frame into a UTF-8 string and returns the
   * converted string.
   */
  @CacheReturn
  String textData();

  @CacheReturn
  Buffer binaryData();

  /**
   * Returns {@code true} if this is the final frame.  This should be {@code true} unless a number of 
   * coninuation frames are expected to follow this frame.
   */
  boolean isFinal();

  static final WebSocketFrameFactory factory = ServiceHelper.loadFactory(WebSocketFrameFactory.class);
}
