/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.examples.echo;

import org.nodex.java.core.Handler;
import org.nodex.java.core.Nodex;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.net.NetClient;
import org.nodex.java.core.net.NetSocket;

public class PerfClient extends NodexMain {

  public static void main(String[] args) throws Exception {
    new EchoClient().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

   public void go() throws Exception {
      new NetClient().connect(8080, "localhost", new Handler<NetSocket>() {
        public void handle(NetSocket socket) {

          final int packetSize = 32 * 1024;
          final long batch = 1024 * 1024 * 100;

          socket.dataHandler(new Handler<Buffer>() {
            int bytesReceived = 0;
            long beginning = System.currentTimeMillis();
            long totalBytes = 0;
            public void handle(Buffer buffer) {
              bytesReceived += buffer.length();

              if (bytesReceived > batch) {
                long end = System.currentTimeMillis();
                totalBytes += bytesReceived;
                double totRate = 1000 * (double)totalBytes / (end - beginning);
                System.out.println("tot rate: bytes / sec " + totRate);
              }
            }
          });

          Buffer buff = Buffer.create(new byte[packetSize]);

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
      Nodex.instance.nextTick(handler);
    } else {
      socket.drainHandler(handler);
    }
  }
}
