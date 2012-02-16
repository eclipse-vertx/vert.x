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

package org.vertx.java.core.http;

import org.vertx.java.core.http.ws.WebSocketFrame;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ServerWebSocket extends WebSocket {

  private static final Logger log = LoggerFactory.getLogger(ServerWebSocket.class);

  private final Runnable connectRunnable;

  public final String path;

  boolean rejected;

  private boolean connected;

  public ServerWebSocket(String path, AbstractConnection conn, Runnable connectRunnable) {
    super(conn);
    this.path = path;
    this.connectRunnable = connectRunnable;
  }

  public void reject() {
    checkClosed();
    if (connected) {
      throw new IllegalStateException("Cannot reject websocket, it has already been written to");
    }
    rejected = true;
  }

  @Override
  protected void writeFrame(WebSocketFrame frame) {
    if (rejected) {
      throw new IllegalStateException("Cannot write to websocket, it has been rejected");
    }
    if (!connected && !closed) {
      connect();
    }
    super.writeFrame(frame);
  }

  // Connect if not already connected
  void connectNow() {
    if (!connected && !rejected) {
      connect();
    }
  }

  @Override
  public void close() {
    if (rejected) {
      throw new IllegalStateException("Cannot close websocket, it has been rejected");
    }
    if (!connected && !closed) {
      connect();
    }
    super.close();
  }

  private void connect() {
    connectRunnable.run();
    connected = true;
  }

}
