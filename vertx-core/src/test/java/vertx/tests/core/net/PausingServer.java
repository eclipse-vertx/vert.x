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

package vertx.tests.core.net;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class PausingServer extends BaseServer {

  public PausingServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        tu.checkThread();
        sock.pause();
        final Handler<Message<Buffer>> resumeHandler = new Handler<Message<Buffer>>() {
          public void handle(Message<Buffer> message) {
            tu.checkThread();
            sock.resume();
          }
        };
        vertx.eventBus().registerHandler("server_resume", resumeHandler);
        sock.closedHandler(new SimpleHandler() {
          public void handle() {
            tu.checkThread();
            vertx.eventBus().unregisterHandler("server_resume", resumeHandler);
          }
        });
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkThread();
          }
        });
      }
    };
  }
}
