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
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketHandler;
import org.vertx.java.core.http.WebSocketVersion;
import org.vertx.java.core.logging.Logger;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebsocketTest extends TestBase {

  private static final Logger log = Logger.getLogger(WebsocketTest.class);

  @Test
  public void testWSBinaryHybi00() throws Exception {
    testWS(true, WebSocketVersion.HYBI_00);
    throwAssertions();
  }

  @Test
  public void testWSStringHybi00() throws Exception {
    testWS(false, WebSocketVersion.HYBI_00);
    throwAssertions();
  }

  @Test
  public void testWSBinaryHybi08() throws Exception {
    testWS(true, WebSocketVersion.HYBI_08);
    throwAssertions();
  }

  @Test
  public void testWSStringHybi08() throws Exception {
    testWS(false, WebSocketVersion.HYBI_08);
    throwAssertions();
  }

  @Test
  public void testWSBinaryHybi17() throws Exception {
    testWS(true, WebSocketVersion.HYBI_17);
    throwAssertions();
  }

  @Test
  public void testWSStringHybi17() throws Exception {
    testWS(false, WebSocketVersion.HYBI_17);
    throwAssertions();
  }

  @Test
  public void testWriteFromConnectHybi00() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_00);
    throwAssertions();
  }

  @Test
  public void testWriteFromConnectHybi08() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_08);
    throwAssertions();
  }

  @Test
  public void testWriteFromConnectHybi17() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_17);
    throwAssertions();
  }


  private void testWS(final boolean binary, final WebSocketVersion version) throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String path = "/some/path";
    final int port = 8181;

    final CountDownLatch latch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final HttpClient client = new HttpClient().setPort(port).setHost(host).setKeepAlive(keepAlive).setMaxPoolSize(5);

        final HttpServer server = new HttpServer().websocketHandler(new WebSocketHandler() {
          public void handle(final WebSocket ws) {

            ws.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                //Echo it back
                ws.writeBuffer(data);
              }
            });
          }

          public boolean accept(String p) {
            azzert(path.equals(p));
            return true;
          }
        }).listen(port, host);

        final int bsize = 100;
        final int sends = 10;

        client.connectWebsocket(path, version, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
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
    });

    azzert(latch.await(5, TimeUnit.SECONDS));
    throwAssertions();
  }

  private void testWriteFromConnectHandler(final WebSocketVersion version) throws Exception {
    final String host = "localhost";
    final boolean keepAlive = true;
    final String path = "/some/path";
    final int port = 8181;

    final CountDownLatch latch = new CountDownLatch(1);

    final Buffer buff = Buffer.create("AAA");

    log.info("buffer is:" + buff);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

        final HttpClient client = new HttpClient().setPort(port).setHost(host).setKeepAlive(keepAlive).setMaxPoolSize(5);

        final HttpServer server = new HttpServer().websocketHandler(new WebSocketHandler() {
          public void handle(final WebSocket ws) {

//            Vertx.instance.setTimer(100, new Handler<Long>() {
//              public void handle(Long id) {
            ws.writeBinaryFrame(buff);
//              }
//            });
          }

          public boolean accept(String p) {
            azzert(path.equals(p));
            return true;
          }
        }).listen(port, host);

        client.connectWebsocket(path, version, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
            final Buffer received = Buffer.create(0);
            ws.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                log.info("received buff: " + data.length());
                received.appendBuffer(data);
                if (received.length() == buff.length()) {
                  azzert(Utils.buffersEqual(buff, received));
                  log.info("buffers equal");
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

            //ws.writeBinaryFrame(buff);
          }
        });
      }
    });

    azzert(latch.await(100000, TimeUnit.SECONDS));
    throwAssertions();
  }
}
