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

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;

/**
 * A TCP/SSL client.<p>
 * Multiple connections to different servers can be made using the same instance.<p>
 * This client supports a configurable number of connection attempts and a configurable
 * delay between attempts.<p>
 * If an instance is instantiated from an event loop then the handlers
 * of the instance will always be called on that same event loop.
 * If an instance is instantiated from some other arbitrary Java thread (i.e. when using embedded) then
 * an event loop will be assigned to the instance and used when any of its handlers
 * are called.<p>
 * Instances of this class are thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public interface NetClient {

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP address. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  @Fluent
  NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler);

  /**
   * Close the client. Any sockets which have not been closed manually will be closed here.
   */
  void close();

}
