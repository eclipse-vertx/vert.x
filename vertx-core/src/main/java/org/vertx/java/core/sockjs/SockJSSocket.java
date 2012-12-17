/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.streams.ReadStream;
import org.vertx.java.core.streams.WriteStream;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * You interact with SockJS clients through instances of SockJS socket.<p>
 * The API is very similar to {@link org.vertx.java.core.http.WebSocket}.
 * It implements both {@link ReadStream} and {@link WriteStream} so it can be used with
 * {@link org.vertx.java.core.streams.Pump} to pump data with flow control.<p>
 * Instances of this class are not thread-safe.<p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class SockJSSocket implements ReadStream, WriteStream {

  private final Handler<Message<Buffer>> writeHandler;
  protected final Vertx vertx;

  /**
   * When a {@code SockJSSocket} is created it automatically registers an event handler with the event bus, the ID of that
   * handler is given by {@code writeHandlerID}.<p>
   * Given this ID, a different event loop can send a buffer to that event handler using the event bus and
   * that buffer will be received by this instance in its own event loop and written to the underlying socket. This
   * allows you to write data to other sockets which are owned by different event loops.
   */
  public final String writeHandlerID;

  public static AtomicInteger cnt = new AtomicInteger();

  protected SockJSSocket(Vertx vertx) {
    this.vertx = vertx;
    this.writeHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> buff) {
        writeBuffer(buff.body);
      }
    };
    this.writeHandlerID = UUID.randomUUID().toString();
    vertx.eventBus().registerLocalHandler(writeHandlerID, writeHandler);
    cnt.incrementAndGet();
  }

  public void close() {
    vertx.eventBus().unregisterHandler(writeHandlerID, writeHandler);
  }

  protected void finalize() throws Throwable {
    cnt.decrementAndGet();
    super.finalize();
  }

}
