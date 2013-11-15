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

package org.vertx.java.core.sockjs.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.UUID;

public abstract class SockJSSocketBase implements SockJSSocket {

  private final Handler<Message<Buffer>> writeHandler;
  protected final Vertx vertx;

  /**
   * When a {@code SockJSSocket} is created it automatically registers an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.<p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying socket. This
   * allows you to write data to other sockets which are owned by different event loops.
   */
  private final String writeHandlerID;

  protected SockJSSocketBase(Vertx vertx) {
    this.vertx = vertx;
    this.writeHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> buff) {
        write(buff.body());
      }
    };
    this.writeHandlerID = UUID.randomUUID().toString();
    vertx.eventBus().registerLocalHandler(writeHandlerID, writeHandler);
  }

  @Override
  public String writeHandlerID() {
    return writeHandlerID;
  }

  @Override
  public void close() {
    vertx.eventBus().unregisterHandler(writeHandlerID, writeHandler);
  }
}
