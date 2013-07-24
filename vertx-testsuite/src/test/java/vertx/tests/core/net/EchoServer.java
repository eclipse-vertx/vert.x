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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EchoServer extends BaseServer {

  public EchoServer() {
    super(true);
  }

  protected EchoServer(boolean sendAppReady) {
    super(sendAppReady);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket socket) {
        tu.checkThread();
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkThread();
            socket.write(buffer);
          }
        });
      }
    };
  }
}
