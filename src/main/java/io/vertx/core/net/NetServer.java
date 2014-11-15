/*
 * Copyright (c) 2011-2013 The original author or authors
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

package io.vertx.core.net;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.metrics.Measured;
import io.vertx.core.streams.ReadStream;

/**
 * Represents a TCP or SSL server<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread (i.e. when running embedded) then
 * and event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances of this class are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface NetServer extends Measured {

  /**
   * Return the connect stream for this server. The server can only have at most one handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link NetSocket} and passes it to the
   * connect stream {@link ReadStream#handler(io.vertx.core.Handler)}.
   *
   * @return the connect stream
   */
  NetSocketStream connectStream();

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link NetSocket} and passes it to the
   * connect handler.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetServer connectHandler(Handler<NetSocket> handler);

  @GenIgnore
  Handler<NetSocket> connectHandler();

  @Fluent
  NetServer listen();

  /**
   * Instruct the server to listen for incoming connections on the specified {@code port} and all available interfaces.
   */
  @Fluent
  NetServer listen(Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Close the server. This will close any currently open connections.
   */
  void close();

  /**
   * Close the server. This will close any currently open connections. The event handler {@code done} will be called
   * when the close is complete.
   */
  void close(Handler<AsyncResult<Void>> completionHandler);

  /**
   * The actual port the server is listening on. This is useful if you bound the server specifying 0 as port number
   * signifying an ephemeral port
   */
  int actualPort();
}
