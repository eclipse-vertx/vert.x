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

package org.vertx.java.core.sockjs;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonObject;

/**
 * A hook that you can use to receive various events on the EventBusBridge.<p>
 */
public interface EventBusBridgeHook {

  /**
   * Called when a new socket is created
   * You can override this method to do things like check the origin header of a socket before
   * accepting it
   * @param sock The socket
   * @return true to accept the socket, false to reject it
   */
  boolean handleSocketCreated(SockJSSocket sock);

  /**
   * The socket has been closed
   * @param sock The socket
   */
  void handleSocketClosed(SockJSSocket sock);

  /**
   * Client is sending or publishing on the socket
   * @param sock The sock
   * @param send if true it's a send else it's a publish
   * @param msg The message
   * @param address The address the message is being sent/published to
   * @return true To allow the send/publish to occur, false otherwise
   */
  boolean handleSendOrPub(SockJSSocket sock, boolean send, JsonObject msg, String address);

  /**
   * Called before client registers a handler
   * @param sock The socket
   * @param address The address
   * @return true to let the registration occur, false otherwise
   */
  boolean handlePreRegister(SockJSSocket sock, String address);

  /**
   * Called after client registers a handler
   * @param sock The socket
   * @param address The address
   */
  void handlePostRegister(SockJSSocket sock, String address);

  /**
   * Client is unregistering a handler
   * @param sock The socket
   * @param address The address
   */
  boolean handleUnregister(SockJSSocket sock, String address);

  /**
   * Called before authorisation - you can override authorisation here if you don't want the default
   * @param message The auth message
   * @param sessionID The session ID
   * @param handler Handler - call this when authorisation is complete
   * @return true if you wish to override authorisation
   */
  boolean handleAuthorise(JsonObject message, String sessionID,
                          Handler<AsyncResult<Boolean>> handler);
}
