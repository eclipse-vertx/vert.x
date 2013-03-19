import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;
import org.vertx.java.testframework.TestUtils;

public class Server extends Verticle {

  public void start() {

    final TestUtils tu = new TestUtils(vertx);

    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        // It's being proxied so should be absolute url
        tu.azzert(req.uri.equals("http://localhost:9192/maven2/io/vertx/mod-maven-test/1.0.0/mod-maven-test-1.0.0.zip"));
        if (req.path.indexOf("..") != -1) {
          req.response.statusCode = 403;
          req.response.end();
        } else {
          //Clearly in a real server you would check the path for better security!!
          req.response.sendFile("." + req.path);
        }
      }
    }).listen(9193, "127.0.0.1");
  }
}
