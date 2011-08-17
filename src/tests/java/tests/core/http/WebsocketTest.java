/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package tests.core.http;

import org.nodex.core.NodexMain;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.Websocket;
import org.nodex.core.http.WebsocketConnectHandler;
import org.testng.annotations.Test;
import tests.Utils;
import tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class WebsocketTest extends TestBase {

  @Test
  public void testWSBinary() throws Exception {
    testWS(true);
    throwAssertions();
  }

  @Test
  public void testWSString() throws Exception {
    testWS(false);
    throwAssertions();
  }

  private void testWS(final boolean binary) throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String path = "/some/path";
    final int port = 8181;

    final CountDownLatch latch = new CountDownLatch(1);

    final HttpClient client = new HttpClient().setPort(port).setHost(host).setKeepAlive(keepAlive).setMaxPoolSize(5);

    new NodexMain() {
      public void go() throws Exception {
        final HttpServer server = new HttpServer(new WebsocketConnectHandler() {
          public boolean onConnect(final Websocket ws) {
            azzert(path.equals(ws.uri));
            ws.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                //Echo it back
                ws.writeBuffer(data);
              }
            });
            return true;
          }
        }).listen(port, host);

        final int bsize = 100;
        final int sends = 10;

        client.connectWebsocket(path, new WebsocketConnectHandler() {
          public boolean onConnect(final Websocket ws) {

            final Buffer received = Buffer.create(0);
            ws.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                received.append(data);
                if (received.length() == bsize * sends) {
                  ws.close();
                  server.close(new Runnable() {
                    public void run() {
                      latch.countDown();
                    }
                  });
                }
              }
            });
            final Buffer sent = Buffer.create(0);
            for (int i = 0; i < sends; i++) {
              if (binary) {
                Buffer buff = Buffer.create(Utils.generateRandomByteArray(bsize));
                ws.writeBinaryFrame(buff);
                sent.append(buff);
              } else {
                String str = Utils.randomAlphaString(100);
                ws.writeTextFrame(str);
                sent.append(Buffer.create(str, "UTF-8"));
              }
            }
            return true;
          }
        });
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }
}
