package org.vertx.java.core.sockjs.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.sockjs.SockJSSocket;

import java.util.UUID;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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
