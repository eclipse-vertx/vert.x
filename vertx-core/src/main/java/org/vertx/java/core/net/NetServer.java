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
import org.vertx.java.core.Handler;
import org.vertx.java.core.ServerSSLSupport;
import org.vertx.java.core.ServerTCPSupport;

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
public interface NetServer extends ServerSSLSupport<NetServer>, ServerTCPSupport<NetServer> {

  /**
   * Supply a connect handler for this server. The server can only have at most one connect handler at any one time.
   * As the server accepts TCP or SSL connections it creates an instance of {@link org.vertx.java.core.net.NetSocket} and passes it to the
   * connect handler.
   * @return a reference to this so multiple method calls can be chained together
   */
  NetServer connectHandler(Handler<NetSocket> connectHandler);

  /**
   * Tell the server to start listening on all available interfaces and port {@code port}. Be aware this is an
   * async operation and the server may not bound on return of the method.
   */
  NetServer listen(int port);

  /**
   * Instruct the server to listen for incoming connections on the specified {@code port} and all available interfaces.
   */
  NetServer listen(int port, Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Tell the server to start listening on port {@code port} and hostname or ip address given by {@code host}. Be aware this is an
   * async operation and the server may not bound on return of the method.
   *
   */
  NetServer listen(int port, String host);

  /**
   * Instruct the server to listen for incoming connections on the specified {@code port} and {@code host}. {@code host} can
   * be a host name or an IP address.
   */
  NetServer listen(int port, String host, Handler<AsyncResult<NetServer>> listenHandler);

  /**
   * Close the server. This will close any currently open connections.
   */
  void close();

  /**
   * Close the server. This will close any currently open connections. The event handler {@code done} will be called
   * when the close is complete.
   */
  void close(Handler<AsyncResult<Void>> doneHandler);

  /**
   * The actual port the server is listening on. This is useful if you bound the server specifying 0 as port number
   * signifying an ephemeral port
   */
  int port();

  /**
   * The host
   */
  String host();
}
