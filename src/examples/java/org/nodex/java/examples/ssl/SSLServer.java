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

package org.nodex.java.examples.ssl;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.net.NetServer;
import org.nodex.java.core.net.NetSocket;

public class SSLServer extends NodexMain {

  public static void main(String[] args) throws Exception {
    new SSLServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new NetServer().connectHandler(new EventHandler<NetSocket>() {
      public void onEvent(final NetSocket socket) {
        socket.dataHandler(new EventHandler<Buffer>() {
          public void onEvent(Buffer buffer) {
            socket.write(buffer);
          }
        });
      }
    }).setSSL(true).setKeyStorePath("server-keystore.jks").setKeyStorePassword("wibble").listen(4443);
  }
}
