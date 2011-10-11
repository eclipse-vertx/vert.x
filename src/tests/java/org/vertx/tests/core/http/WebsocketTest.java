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

package org.vertx.tests.core.http;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.VertxMain;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.Websocket;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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



    new VertxMain() {
      public void go() throws Exception {

        final HttpClient client = new HttpClient().setPort(port).setHost(host).setKeepAlive(keepAlive).setMaxPoolSize(5);

        final HttpServer server = new HttpServer().websocketHandler(new Handler<Websocket>() {
          public void handle(final Websocket ws) {
            azzert(path.equals(ws.uri));
            ws.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                //Echo it back
                ws.writeBuffer(data);
              }
            });
          }
        }).listen(port, host);

        final int bsize = 100;
        final int sends = 10;

        client.connectWebsocket(path, new Handler<Websocket>() {
          public void handle(final Websocket ws) {

            final Buffer received = Buffer.create(0);
            ws.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                received.appendBuffer(data);
                if (received.length() == bsize * sends) {
                  ws.close();
                  server.close(new SimpleHandler() {
                    public void handle() {
                      client.close();
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
                sent.appendBuffer(buff);
              } else {
                String str = Utils.randomAlphaString(100);
                ws.writeTextFrame(str);
                sent.appendBuffer(Buffer.create(str, "UTF-8"));
              }
            }
          }
        });
      }
    }.run();

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }
}
