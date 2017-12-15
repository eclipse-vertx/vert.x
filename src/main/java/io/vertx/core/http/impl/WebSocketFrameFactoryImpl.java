/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
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

package io.vertx.core.http.impl;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.spi.WebSocketFrameFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebSocketFrameFactoryImpl implements WebSocketFrameFactory {


  @Override
  public WebSocketFrame binaryFrame(Buffer data, boolean isFinal) {
    return new WebSocketFrameImpl(FrameType.BINARY, data.getByteBuf(), isFinal);
  }

  @Override
  public WebSocketFrame textFrame(String str, boolean isFinal) {
    return new WebSocketFrameImpl(str, isFinal);
  }

  @Override
  public WebSocketFrame continuationFrame(Buffer data, boolean isFinal) {
    return new WebSocketFrameImpl(FrameType.CONTINUATION, data.getByteBuf(), isFinal);
  }

  @Override
  public WebSocketFrame pingFrame(Buffer data) {
    return new WebSocketFrameImpl(FrameType.PING, data.getByteBuf(), true);
  }

  @Override
  public WebSocketFrame pongFrame(Buffer data) {
    return new WebSocketFrameImpl(FrameType.PONG, data.getByteBuf(), true);
  }
}
