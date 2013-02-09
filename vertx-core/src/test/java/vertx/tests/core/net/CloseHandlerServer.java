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
import org.vertx.java.core.net.NetSocket;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CloseHandlerServer extends BaseServer {

  protected boolean closeFromServer;

  public CloseHandlerServer() {
    super(true);
    closeFromServer = false;
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      final AtomicInteger counter = new AtomicInteger(0);
      public void handle(final NetSocket sock) {
        tu.checkThread();
        sock.endHandler(new SimpleHandler() {
          public void handle() {
            tu.checkThread();
            tu.azzert(counter.incrementAndGet() == 1);
          }
        });
        sock.closedHandler(new SimpleHandler() {
          public void handle() {
            tu.checkThread();
            tu.azzert(counter.incrementAndGet() == 2);
            tu.testComplete();
          }
        });
        if (closeFromServer) {
          sock.close();
        }
      }
    };
  }
}
