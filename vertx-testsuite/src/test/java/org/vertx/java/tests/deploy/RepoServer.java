package org.vertx.java.tests.deploy;

import java.net.URI;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

public class RepoServer {
  private static final Logger log = LoggerFactory.getLogger(RepoServer.class);
  private static final String webroot = "src/test/resources/";
  private final Vertx vertx;

  public RepoServer(final String module1, final String module2) {
    vertx = Vertx.newVertx();
    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {
        log.info("HANDLING " + req.uri);
        if (URI.create(req.uri).getPath().equals("/vertx-mods/mods/" + module2 + "/mod.zip")) {
          log.info("HANDLING repo request");
          req.response.sendFile(webroot + "mod2.zip");
        } else if (req.uri.equals("http://vert-x.github.com:80/vertx-mods/mods/" + module1 + "/mod.zip")) {
          req.response.sendFile(webroot + "mod1.zip");
        } else {
        	log.error("File not found: " + req.uri);
        }
      }
    }).listen(9093, "localhost");
  }

}