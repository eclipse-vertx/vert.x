/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.streams.ReadStream;

/**
 * A stream for {@link HttpClient} WebSocket connection.
 * <p>
 * When the connection attempt is successful, the stream handler is called back with the {@link io.vertx.core.http.WebSocket}
 * argument, immediately followed by a call to the end handler. When the connection attempt fails, the exception handler is invoked.
 * <p>
 * The connection occurs when the {@link #handler} method is called with a non null handler, the other handlers should be
 * set before setting the handler.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface WebSocketStream extends ReadStream<WebSocket> {

  @Override
  WebSocketStream exceptionHandler(Handler<Throwable> handler);

  @Override
  WebSocketStream handler(Handler<WebSocket> handler);

  @Override
  WebSocketStream pause();

  @Override
  WebSocketStream resume();

  @Override
  WebSocketStream endHandler(Handler<Void> endHandler);

}
