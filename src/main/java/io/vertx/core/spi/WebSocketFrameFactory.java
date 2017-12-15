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

package io.vertx.core.spi;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.WebSocketFrame;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface WebSocketFrameFactory {

  WebSocketFrame binaryFrame(Buffer data, boolean isFinal);

  WebSocketFrame textFrame(String str, boolean isFinal);

  WebSocketFrame continuationFrame(Buffer data, boolean isFinal);

  WebSocketFrame pingFrame(Buffer data);

  WebSocketFrame pongFrame(Buffer data);
}
