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

package vertx.tests.core.sockjs;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.java.deploy.Container;
import org.vertx.java.framework.TestClientBase;
import org.vertx.java.framework.TestUtils;

/**
 * Some basic SockJS tests - most testing is done via the python protocol test and the JS client tests,
 * so these are pretty basic
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testWebSockets() {

    final HttpServer server = new HttpServer();
    SockJSServer sockJSServer = new SockJSServer(server);

    final String path = "/app/path";

    sockJSServer.installApp(new AppConfig().setPrefix(path), new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            // Just echo the data back
            sock.writeBuffer(buff);
          }
        });
      }
    });

    server.listen(8080);

    final HttpClient client = new HttpClient().setPort(8080);

    client.connectWebsocket("/app/path/serverid/sessionid1/websocket", new Handler<WebSocket>() {

      public void handle(WebSocket ws) {

        String strSend = "[\"" + TestUtils.randomAlphaString(10) + "\"]";

        // What we expect to receive
        String strReceive = "oa" + strSend;
        final Buffer buffRec = Buffer.create(strReceive);

        final Buffer received = Buffer.create(1000);
        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {

            received.appendBuffer(data);
            if (received.length() == buffRec.length()) {

              tu.azzert(TestUtils.buffersEqual(buffRec, received));

              client.close();

              server.close(new SimpleHandler() {
                public void handle() {
                  tu.testComplete();
                }
              });
            }
          }
        });

        ws.writeTextFrame(strSend);
      }
    });
  }

  public void testXHRPolling() {
    
    final Logger log = Container.instance.getLogger();

    final HttpServer server = new HttpServer();
    SockJSServer sockJSServer = new SockJSServer(server);

    final String path = "/app/path";

    sockJSServer.installApp(new AppConfig().setPrefix(path), new Handler<SockJSSocket>() {
      public void handle(final SockJSSocket sock) {
        sock.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer buff) {
            // Just echo the data back
            sock.writeBuffer(buff);
          }
        });
      }
    });

    server.listen(8080);

    final HttpClient client = new HttpClient().setPort(8080).setMaxPoolSize(2);

    String strSend = "[\"" + TestUtils.randomAlphaString(1000) + "\"]";
    String strRec = "a" + strSend + "\n";
    final Buffer buffRec = Buffer.create(strRec);

    HttpClientRequest req = client.post("/app/path/serverid/sessionid2/xhr", new Handler<HttpClientResponse>() {

      public void handle(HttpClientResponse resp) {

        final Buffer buff = Buffer.create(0);
        resp.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            buff.appendBuffer(data);
          }
        });

        resp.endHandler(new SimpleHandler() {
          public void handle() {

            tu.azzert(TestUtils.buffersEqual(Buffer.create("o\n"), buff));

              client.post("/app/path/serverid/sessionid2/xhr", new Handler<HttpClientResponse>() {

                public void handle(HttpClientResponse resp) {

                  final Buffer buff = Buffer.create(0);

                  resp.dataHandler(new Handler<Buffer>() {
                    public void handle(Buffer data) {
                      buff.appendBuffer(data);
                    }
                  });

                  resp.endHandler(new SimpleHandler() {
                    public void handle() {

                      tu.azzert(TestUtils.buffersEqual(buffRec, buff));

                      client.close();

                      server.close(new SimpleHandler() {
                        public void handle() {
                          tu.testComplete();
                        }
                      });
                    }
                  });

                }
              }).end();
          }
        });
      }
    });
    req.end();

    req = client.post("/app/path/serverid/sessionid2/xhr_send", new Handler<HttpClientResponse>() {

      public void handle(HttpClientResponse resp) {
        tu.azzert(204 == resp.statusCode);
      }
    });

    req.end(strSend);

  }

}
