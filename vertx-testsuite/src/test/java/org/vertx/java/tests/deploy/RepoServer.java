package org.vertx.java.tests.deploy;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;

public class RepoServer {
  private static final String webroot = "src/test/resources/";
  private final Vertx vertx;

  public RepoServer(final String module1, final String module2) {
    vertx = Vertx.newVertx();
    vertx.createHttpServer()
        .requestHandler(new Handler<HttpServerRequest>() {
          public void handle(final HttpServerRequest req) {
            System.out.println("HANDLING " + req.uri);
            if (req.uri.equals("/vertx-mods/mods/" + module2
                + "/mod.zip")) {
              System.out.println("HANDLING repo request");
              req.response.sendFile(webroot + "mod2.zip");
            } else if (req.uri.equals("http://vert-x.github.com/vertx-mods/mods/" + module1
                + "/mod.zip")) {
              req.response.sendFile(webroot + "mod1.zip");
            }
          }
        }).listen(9093, "localhost");
  }

}