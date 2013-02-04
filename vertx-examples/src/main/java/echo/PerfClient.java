package echo;

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
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.platform.Verticle;

public class PerfClient extends Verticle {

  public void start() {
    vertx.createNetClient().connect(1234, "localhost", new Handler<NetSocket>() {
      public void handle(NetSocket socket) {

        final int packetSize = 32 * 1024;
        final int batch = 1024 * 1024 * 512;

        socket.dataHandler(new Handler<Buffer>() {
          int bytesReceived = 0;
          long start = System.currentTimeMillis();
          public void handle(Buffer buffer) {
            bytesReceived += buffer.length();
            if (bytesReceived > batch) {
              long end = System.currentTimeMillis();
              double rate = 1000 * (double)bytesReceived / (end - start);
              double mbitsRate = rate * 8 / (1024 * 1024);
              System.out.println("rate: " + rate + " bytes/sec " + mbitsRate + " Mbits/sec");
              bytesReceived = 0;
              start = end;
            }
          }
        });

        Buffer buff = new Buffer(new byte[packetSize]);

        sendData(socket, buff);
      }
    });
  }

  private void sendData(final NetSocket socket, final Buffer buff) {
    socket.write(buff);
    SimpleHandler handler = new SimpleHandler() {
      public void handle() {
        sendData(socket, buff);
      }
    };
    if (!socket.writeQueueFull()) {
      vertx.runOnLoop(handler);
    } else {
      socket.drainHandler(handler);
    }
  }
}
