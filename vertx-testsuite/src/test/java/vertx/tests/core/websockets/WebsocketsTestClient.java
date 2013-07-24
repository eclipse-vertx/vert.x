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

package vertx.tests.core.websockets;

import org.vertx.java.core.*;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.*;
import org.vertx.java.core.impl.CaseInsensitiveMultiMap;
import org.vertx.java.core.json.impl.Base64;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.testframework.TestClientBase;
import org.vertx.java.testframework.TestUtils;

import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class WebsocketsTestClient extends TestClientBase {

  private HttpClient client;
  private HttpServer server;

  @Override
  public void start() {
    super.start();
    client = vertx.createHttpClient().setHost("localhost").setPort(8080);
    tu.appReady();
  }

  @Override
  public void stop() {
    client.close();
    if (server != null) {
      server.close(new AsyncResultHandler<Void>() {
        public void handle(AsyncResult<Void> result) {
          tu.checkThread();
          WebsocketsTestClient.super.stop();
        }
      });
    } else {
      super.stop();
    }
  }

  public void testRejectHybi00() throws Exception {
    testReject(WebSocketVersion.HYBI_00);
  }

  public void testRejectHybi08() throws Exception {
    testReject(WebSocketVersion.HYBI_08);
  }

  public void testWSBinaryHybi00() throws Exception {
    testWS(true, WebSocketVersion.HYBI_00);
  }

  public void testWSStringHybi00() throws Exception {
    testWS(false, WebSocketVersion.HYBI_00);
  }

  public void testWSBinaryHybi08() throws Exception {
    testWS(true, WebSocketVersion.HYBI_08);
  }

  public void testWSStringHybi08() throws Exception {
    testWS(false, WebSocketVersion.HYBI_08);
  }

  public void testWSBinaryHybi17() throws Exception {
    testWS(true, WebSocketVersion.RFC6455);
  }

  public void testWSStringHybi17() throws Exception {
    testWS(false, WebSocketVersion.RFC6455);
  }

  public void testWriteFromConnectHybi00() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_00);
  }

  public void testWriteFromConnectHybi08() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_08);
  }

  public void testWriteFromConnectHybi17() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.RFC6455);
  }

  // TODO close and exception tests
  // TODO pause/resume/drain tests
  // TODO websockets over HTTPS tests

  private String sha1(String s) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA1");
      //Hash the data
      byte[] bytes = md.digest(s.getBytes("UTF-8"));
      String encoded = Base64.encodeBytes(bytes);
      return encoded;
    } catch (Exception e) {
      throw new InternalError("Failed to compute sha-1");
    }
  }

  // Let's manually handle the websocket handshake and write a frame to the client
  public void testHandleWSManually() throws Exception {
    final String path = "/some/path";

    final String message = "here is some text data";

    server = vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        tu.checkThread();
        tu.azzert(path.equals(req.path()));
        tu.azzert("Upgrade".equals(req.headers().get("Connection")));
        NetSocket sock = req.netSocket();
        String secHeader = req.headers().get("Sec-WebSocket-Key");
        String tmp = secHeader + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
        String encoded = sha1(tmp);
        sock.write("HTTP/1.1 101 Web Socket Protocol Handshake\r\n" +
                   "Upgrade: WebSocket\r\n" +
                   "Connection: Upgrade\r\n" +
                   "Sec-WebSocket-Accept: " + encoded + "\r\n" +
                   "\r\n");
        // Let's write a Text frame raw
        Buffer buff = new Buffer();
        buff.appendByte((byte)129); // Text frame
        buff.appendByte((byte)message.length());
        buff.appendString(message);
        sock.write(buff);
      }

    });
    server.listen(8080, "localhost", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.connectWebsocket(path, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
            ws.dataHandler(new Handler<Buffer>() {
              @Override
              public void handle(Buffer buff) {
                tu.azzert(message.equals(buff.toString("UTF-8")));
                tu.testComplete();
              }
            });
          }
        });
        client.exceptionHandler(new Handler<Throwable>() {
          @Override
          public void handle(Throwable t) {
            t.printStackTrace();
          }
        });
      }
    });
  }

  private void testWS(final boolean binary, final WebSocketVersion version) throws Exception {

    final String path = "/some/path";
    final String query = "foo=bar&wibble=eek";

    server = vertx.createHttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {
        tu.checkThread();
        tu.azzert(path.equals(ws.path()));
        tu.azzert(query.equals(ws.query()));

        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.checkThread();
            //Echo it back
            ws.write(data);
          }
        });
      }

    });
    server.listen(8080, "localhost", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        final int bsize = 100;
        final int sends = 10;

        client.connectWebsocket(path + "?" + query, version, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
            tu.checkThread();
            final Buffer received = new Buffer();
             ws.dataHandler(new Handler<Buffer>() {
               public void handle(Buffer data) {
                 tu.checkThread();
                 received.appendBuffer(data);
                 if (received.length() == bsize * sends) {
                   ws.close();
                   tu.testComplete();
                 }
               }
             });
             final Buffer sent = new Buffer();
               for (int i = 0; i < sends; i++) {
               if (binary) {
                 Buffer buff = new Buffer(TestUtils.generateRandomByteArray(bsize));
                 ws.writeBinaryFrame(buff);
                 sent.appendBuffer(buff);
               } else {
                 String str = TestUtils.randomAlphaString(bsize);
                 ws.writeTextFrame(str);
                 sent.appendBuffer(new Buffer(str, "UTF-8"));
               }
             }
          }
        });
      }
    });
  }

  private void testWriteFromConnectHandler(final WebSocketVersion version) throws Exception {

    final String path = "/some/path";

    final Buffer buff = new Buffer("AAA");

    server = vertx.createHttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {
        tu.azzert(path.equals(ws.path()));
        ws.writeBinaryFrame(buff);
      }
    });
    server.listen(8080, "localhost", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.connectWebsocket(path, version, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
            final Buffer received = new Buffer();
            ws.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                received.appendBuffer(data);
                if (received.length() == buff.length()) {
                  tu.azzert(TestUtils.buffersEqual(buff, received));
                  ws.close();
                  tu.testComplete();
                }
              }
            });
          }
        });
      }
    });
  }

  private void testReject(final WebSocketVersion version) throws Exception {

    final String path = "/some/path";

    server = vertx.createHttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {

        tu.checkThread();
        tu.azzert(path.equals(ws.path()));
        ws.reject();
      }

    });
    server.listen(8080, "localhost", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.exceptionHandler(new Handler<Throwable>() {
          public void handle(Throwable t) {
            tu.testComplete();
          }
        });

        client.connectWebsocket(path, version, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
            tu.azzert(false, "Should not be called");
          }
        });
      }
    });
  }

  public void testSharedServersMultipleInstances1() {
    final int numConnections = vertx.sharedData().<String, Integer>getMap("params").get("numConnections");
    final AtomicInteger counter = new AtomicInteger(0);
    for (int i = 0; i < numConnections; i++) {
      client.exceptionHandler(new Handler<Throwable>() {
        @Override
        public void handle(Throwable t) {
          t.printStackTrace();
        }
      });
      client.connectWebsocket("http://somehost", new Handler<WebSocket>() {
        public void handle(WebSocket ws) {
          ws.closeHandler(new VoidHandler() {
            public void handle() {
              int count = counter.incrementAndGet();
              if (count == numConnections) {
                tu.testComplete();
              }
            }
          });
        }
      });
    }
  }

  public void testSharedServersMultipleInstances2() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances1StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances2StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3StartAllStopAll() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances1StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances2StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testSharedServersMultipleInstances3StartAllStopSome() {
    testSharedServersMultipleInstances1();
  }

  public void testHeaders() {
    final MultiMap extraHeaders = new CaseInsensitiveMultiMap();
    extraHeaders.set("armadillos", "yes");
    extraHeaders.set("shoes", "yellow");
    extraHeaders.set("hair", "purple");
    server = vertx.createHttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {
        tu.checkThread();
//        for (Map.Entry<String, String> entry: ws.headers().entrySet()) {
//          System.out.println("header, key:" + entry.getKey() + ":" + entry.getValue());
//        }
        tu.azzert(ws.headers().get("upgrade").equals("websocket"));
        tu.azzert(ws.headers().get("connection").equals("Upgrade"));
        tu.azzert(ws.headers().get("host").equals("localhost:8080"));
        for (Map.Entry<String, String> entry: extraHeaders) {
          tu.azzert(ws.headers().get(entry.getKey()).equals(entry.getValue()));
        }
        tu.testComplete();
      }
    });
    server.listen(8080, "localhost", new AsyncResultHandler<HttpServer>() {
      @Override
      public void handle(AsyncResult<HttpServer> ar) {
        tu.azzert(ar.succeeded());
        client.connectWebsocket("/foo", WebSocketVersion.RFC6455, extraHeaders, new Handler<WebSocket>() {
          public void handle(final WebSocket ws) {
            tu.checkThread();
          }
        });
      }
    });
  }

}
