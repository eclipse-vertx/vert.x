package org.foo;

import com.acme.OtherClass;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.platform.Verticle;

public class MyMain extends Verticle {

  public void start() {

    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(HttpServerRequest req) {
        System.out.println("Got request: " + req.uri);
        req.response.headers().put("Content-Type", "text/html; charset=UTF-8");
        // Make sure we can reference another source file:
        OtherClass otherClass = new OtherClass();
        req.response.end("<html><body><h1>Hello from vert.x! " + otherClass.date() + "</h1></body></html>");
      }
    }).listen(8080);
  }
}