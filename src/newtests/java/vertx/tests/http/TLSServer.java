package vertx.tests.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestUtils;
import org.vertx.java.tests.TLSTestParams;
import org.vertx.java.tests.net.JavaNetTest;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  protected ContextChecker check;

  public void start() {
    check = new ContextChecker(tu);

    TLSTestParams params = SharedData.<String, TLSTestParams>getMap("TLSTest").get("params");

    server = new HttpServer();

    server.setSSL(true);

    if (params.serverTrust) {
      server.setTrustStorePath("./src/tests/keystores/server-truststore.jks").setTrustStorePassword
          ("wibble");
    }
    if (params.serverCert) {
      server.setKeyStorePath("./src/tests/keystores/server-keystore.jks").setKeyStorePassword("wibble");
    }
    if (params.requireClientAuth) {
      server.setClientAuthRequired(true);
    }

    server.requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {

        check.check();

        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            check.check();
            tu.azzert("foo".equals(buffer.toString()));
            req.response.end("bar");
          }
        });
      }
    }).listen(4043);

    tu.appReady();
  }

  public void stop() {
    server.close(new SimpleHandler() {
      public void handle() {
        check.check();
        tu.appStopped();
      }
    });
  }

}
