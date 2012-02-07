package vertx.tests.core.websockets;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.ServerWebSocket;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketVersion;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestClientBase;
import org.vertx.java.newtests.TestUtils;

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
    client = new HttpClient().setHost("localhost").setPort(8080);
    tu.appReady();
  }

  @Override
  public void stop() {
    client.close();
    if (server != null) {
      server.close(new SimpleHandler() {
        public void handle() {
          tu.checkContext();
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
    testWS(true, WebSocketVersion.HYBI_17);
  }

  public void testWSStringHybi17() throws Exception {
    testWS(false, WebSocketVersion.HYBI_17);
  }

  public void testWriteFromConnectHybi00() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_00);
  }

  public void testWriteFromConnectHybi08() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_08);
  }

  public void testWriteFromConnectHybi17() throws Exception {
    testWriteFromConnectHandler(WebSocketVersion.HYBI_17);
  }

  // TODO close and exception tests
  // TODO pause/resume/drain tests
  // TODO websockets over HTTPS tests

  private void testWS(final boolean binary, final WebSocketVersion version) throws Exception {

    final String path = "/some/path";

    server = new HttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {
        tu.checkContext();
        tu.azzert(path.equals(ws.path));

        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.checkContext();
            //Echo it back
            ws.writeBuffer(data);
          }
        });
      }

    }).listen(8080, "localhost");

    final int bsize = 100;
    final int sends = 10;

    client.connectWebsocket(path, version, new Handler<WebSocket>() {
      public void handle(final WebSocket ws) {
        tu.checkContext();
        final Buffer received = Buffer.create(0);
        ws.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
            tu.checkContext();
            received.appendBuffer(data);
            if (received.length() == bsize * sends) {
              ws.close();
              tu.testComplete();
            }
          }
        });
        final Buffer sent = Buffer.create(0);
        for (int i = 0; i < sends; i++) {
          if (binary) {
            Buffer buff = Buffer.create(TestUtils.generateRandomByteArray(bsize));
            ws.writeBinaryFrame(buff);
            sent.appendBuffer(buff);
          } else {
            String str = TestUtils.randomAlphaString(100);
            ws.writeTextFrame(str);
            sent.appendBuffer(Buffer.create(str, "UTF-8"));
          }
        }
      }
    });
  }

  private void testWriteFromConnectHandler(final WebSocketVersion version) throws Exception {

    final String path = "/some/path";

    final Buffer buff = Buffer.create("AAA");

    server = new HttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {
        tu.azzert(path.equals(ws.path));
        ws.writeBinaryFrame(buff);
      }
    }).listen(8080, "localhost");

    client.connectWebsocket(path, version, new Handler<WebSocket>() {
      public void handle(final WebSocket ws) {
        final Buffer received = Buffer.create(0);
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

  private void testReject(final WebSocketVersion version) throws Exception {

    final String path = "/some/path";

    server = new HttpServer().websocketHandler(new Handler<ServerWebSocket>() {
      public void handle(final ServerWebSocket ws) {

        tu.checkContext();
        tu.azzert(path.equals(ws.path));
        ws.reject();
      }

    }).listen(8080, "localhost");

    client.exceptionHandler(new Handler<Exception>() {
      public void handle(Exception e) {
        tu.testComplete();
      }
    });

    client.connectWebsocket(path, version, new Handler<WebSocket>() {
      public void handle(final WebSocket ws) {
        tu.azzert(false, "Should not be called");
      }
    });
  }

  public void testSharedServersMultipleInstances1() {
    final int numConnections = SharedData.instance.<String, Integer>getMap("params").get("numConnections");
    final AtomicInteger counter = new AtomicInteger(0);
    for (int i = 0; i < numConnections; i++) {
      client.connectWebsocket("someurl", new Handler<WebSocket>() {
        public void handle(WebSocket ws) {
          ws.closedHandler(new SimpleHandler() {
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

}
