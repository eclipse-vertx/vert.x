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
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.testframework.TestUtils;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DrainingServer extends BaseServer {

  public DrainingServer() {
    super(true);
  }

  protected Handler<NetSocket> getConnectHandler() {
    return new Handler<NetSocket>() {
      public void handle(final NetSocket sock) {
        tu.checkThread();

        tu.azzert(!sock.writeQueueFull());
        sock.setWriteQueueMaxSize(1000);

        final Buffer buff = TestUtils.generateRandomBuffer(10000);
        //Send data until the buffer is full
        vertx.setPeriodic(0, new Handler<Long>() {
          public void handle(Long id) {
            tu.checkThread();
            sock.write(buff);
            if (sock.writeQueueFull()) {
              vertx.cancelTimer(id);
              sock.drainHandler(new SimpleHandler() {
                public void handle() {
                  tu.checkThread();
                  tu.azzert(!sock.writeQueueFull());
                  // End test after a short delay to give the client some time to read the data
                  vertx.setTimer(100, new Handler<Long>() {
                    public void handle(Long id) {
                      tu.testComplete();
                    }
                  });
                }
              });

              // Tell the client to resume
              vertx.eventBus().send("client_resume", "");
            }
          }
        });
      }
    };
  }
}
