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
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.impl.FrameType;

/**
 * A Web Socket frame that represents either text or binary data.
 *
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * @version $Rev: 2080 $, $Date: 2010-01-26 18:04:19 +0900 (Tue, 26 Jan 2010) $
 */
public interface WebSocketFrameInternal extends WebSocketFrame {
  /**
   * Returns the content of this frame as-is, with no UTF-8 decoding.
   */
  ByteBuf getBinaryData();

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

  FrameType type();
}
