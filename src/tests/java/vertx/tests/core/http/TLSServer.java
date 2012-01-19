package vertx.tests.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.TestUtils;
import org.vertx.java.tests.core.TLSTestParams;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TLSServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  public void start() {
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

        tu.checkContext();

        req.bodyHandler(new Handler<Buffer>() {
          public void handle(Buffer buffer) {
            tu.checkContext();
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
        tu.checkContext();
        tu.appStopped();
      }
    });
  }

}
