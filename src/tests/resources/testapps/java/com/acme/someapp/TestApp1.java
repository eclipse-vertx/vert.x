package com.acme.someapp;

import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.http.HttpServer;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestApp1 implements VertxApp {

  private static AtomicLong instanceCount = new AtomicLong(0);

  public TestApp1() {
    instanceCount.incrementAndGet();
  }

  private HttpServer server;

  @Override
  public void start() {
    System.out.println("Starting app");

//    server = new HttpServer().requestHandler(new Handler<HttpServerRequest>() {
//      public void handle(HttpServerRequest req) {
//        //System.out.println("Got request: " + req.uri + " in " + TestApp1.this);
//        req.response.end(String.valueOf(System.identityHashCode(TestApp1.this)));
//      }
//    }).listen(8080, "localhost");

    System.out.println("count is " + instanceCount.get());
  }

  @Override
  public void stop() {
    System.out.println("Stopping app");

    server.close();
  }
}

