package vertx.tests.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServer;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.net.NetServer;
import org.vertx.java.core.net.NetSocket;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.newtests.ContextChecker;
import org.vertx.java.newtests.TestUtils;
import org.vertx.java.tests.http.RequestInfo;
import org.vertx.java.tests.http.ResponseInfo;

import java.util.Map;
import java.util.Set;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class EchoServer implements VertxApp {

  protected TestUtils tu = new TestUtils();

  private HttpServer server;

  protected ContextChecker check;

  public void start() {
    check = new ContextChecker(tu);

    server = new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        check.check();

        System.out.println("Received request");

        RequestInfo reqInfo = SharedData.<String, RequestInfo>getMap("params").get("requestinfo");
        ResponseInfo respinfo = SharedData.<String, ResponseInfo>getMap("params").get("responseinfo");

        System.out.println("uri:" + req.uri);
        System.out.println("path:" + req.path);
        System.out.println("query:" + req.query);

        tu.azzert(reqInfo.method.equals(req.method));
        tu.azzert(reqInfo.uri.equals(req.uri));
        tu.azzert(reqInfo.path == null ? req.path == null : reqInfo.path.equals(req.path));
        tu.azzert(reqInfo.query == null ? req.query == null : reqInfo.query.equals(req.query));

        if (reqInfo.headers != null) {
          for (Map.Entry<String, String> header: reqInfo.headers.entrySet()) {
            tu.azzert(req.getHeaders().get(header.getKey()).equals(header.getValue()));
          }
        }

        req.response.statusCode = respinfo.statusCode;
        req.response.statusMessage = respinfo.statusMessage;

        req.response.end();
      }
    }).listen(8080);

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
