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

package org.vertx.java.core.net;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.ClientSSLSupport;
import org.vertx.java.core.Handler;
import org.vertx.java.core.TCPSupport;

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
public interface NetClient extends ClientSSLSupport<NetClient>, TCPSupport<NetClient> {

  /**
   * Attempt to open a connection to a server at the specific {@code port} and host {@code localhost}
   * The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, Handler<AsyncResult<NetSocket>> connectCallback);

  /**
   * Attempt to open a connection to a server at the specific {@code port} and {@code host}.
   * {@code host} can be a valid host name or IP address. The connect is done asynchronously and on success, a
   * {@link NetSocket} instance is supplied via the {@code connectHandler} instance
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient connect(int port, String host, Handler<AsyncResult<NetSocket>> connectHandler);

  /**
   * Set the number of reconnection attempts. In the event a connection attempt fails, the client will attempt
   * to connect a further number of times, before it fails. Default value is zero.
   */
  NetClient setReconnectAttempts(int attempts);

  /**
   * Get the number of reconnect attempts
   */
  int getReconnectAttempts();

  /**
   * Set the reconnect interval, in milliseconds
   */
  NetClient setReconnectInterval(long interval);

  /**
   * Get the reconnect interval, in milliseconds.
   */
  long getReconnectInterval();

  /**
   * Set the connect timeout in milliseconds.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetClient setConnectTimeout(int timeout);

  /**
   *
   * @return The connect timeout in milliseconds
   */
  int getConnectTimeout();

  /**
   * Close the client. Any sockets which have not been closed manually will be closed here.
   */
  void close();

}
