package ssl;

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;

public class SSLClient extends Verticle {

  public void start() {
    vertx.createNetClient().setSSL(true).setTrustAll(true).connect(1234, "localhost", new Handler<NetSocket>() {
      public void handle(NetSocket socket) {
        socket.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            System.out.println("Net client receiving: " + buffer.toString("UTF-8"));
          }
        });
        //Now send some dataHandler
        for (int i = 0; i < 10; i++) {
          String str = "hello" + i + "\n";
          System.out.print("Net client sending: " + str);
          socket.write(new Buffer(str));
        }
      }
    });
  }
}
