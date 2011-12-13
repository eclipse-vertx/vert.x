package org.vertx.tests.core.sockjs;

import org.testng.annotations.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.internal.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.sockjs.AppConfig;
import org.vertx.java.core.sockjs.SockJSServer;
import org.vertx.java.core.sockjs.SockJSSocket;
import org.vertx.tests.Utils;
import org.vertx.tests.core.TestBase;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Some basic SockJS tests - most testing is done via the python protocol test, so these are pretty basic
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class SockJSTest extends TestBase {

  private static final Logger log = Logger.getLogger(SockJSTest.class);

  @Test
  public void testWebSockets() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

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

        client.connectWebsocket("/app/path/serverid/sessionid/websocket", new Handler<WebSocket>() {

          public void handle(WebSocket ws) {

            String strSend = "[\"" + Utils.randomAlphaString(10) + "\"]";


            // What we expect to receive
            String strReceive = "oa" + strSend;
            final Buffer buffRec = Buffer.create(strReceive);


            final Buffer received = Buffer.create(1000);
            ws.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {

                log.info("received data: " + data);

                received.appendBuffer(data);
                log.info("received is now: " + received);
                if (received.length() == buffRec.length()) {

                  azzert(Utils.buffersEqual(buffRec, received));

                  client.close();

                  server.close(new SimpleHandler() {
                    public void handle() {
                      latch.countDown();
                    }
                  });
                }
              }
            });

            //ws.writeTextFrame(strSend);
          }
        });
      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }


  @Test
  public void testXHRPolling() throws Exception {

    final CountDownLatch latch = new CountDownLatch(1);

    VertxInternal.instance.go(new Runnable() {
      public void run() {

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

        HttpClientRequest req = client.post("/app/path/serverid/sessionid/xhr", new Handler<HttpClientResponse>() {

          public void handle(HttpClientResponse resp) {

            final Buffer buff = Buffer.create(0);
            resp.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                buff.appendBuffer(data);
              }
            });

            resp.endHandler(new SimpleHandler() {
              public void handle() {
                azzert(Utils.buffersEqual(Buffer.create("o\n"), buff));
              }
            });
          }
        });
        req.end();

        String strSend = "[\"" + Utils.randomAlphaString(1000) + "\"]";
        String strRec = "a" + strSend + "\n";
        final Buffer buffRec = Buffer.create(strRec);

        client.post("/app/path/serverid/sessionid/xhr", new Handler<HttpClientResponse>() {

          public void handle(HttpClientResponse resp) {

            final Buffer buff = Buffer.create(0);

            resp.dataHandler(new Handler<Buffer>() {
              public void handle(Buffer data) {
                buff.appendBuffer(data);
              }
            });

            resp.endHandler(new SimpleHandler() {
              public void handle() {
                azzert(Utils.buffersEqual(buffRec, buff));

                client.close();

                server.close(new SimpleHandler() {
                  public void handle() {
                    latch.countDown();
                  }
                });
              }
            });

          }
        }).end();

        req = client.post("/app/path/serverid/sessionid/xhr_send", new Handler<HttpClientResponse>() {

          public void handle(HttpClientResponse resp) {
            azzert(204 == resp.statusCode);
          }
        });


        req.end(strSend);

      }
    });

    azzert(latch.await(5, TimeUnit.SECONDS));

    throwAssertions();
  }
}
