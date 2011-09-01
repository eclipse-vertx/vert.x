package org.nodex.examples.https;

import org.nodex.core.EventHandler;
import org.nodex.core.NodexMain;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;

public class ServerExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new ServerExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new HttpServer().requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(HttpServerRequest req) {
        System.out.println("Got request: " + req.uri);
        System.out.println("Headers are: ");
        for (String key : req.getHeaderNames()) {
          System.out.println(key + ":" + req.getHeader(key));
        }
        req.response.putHeader("Content-Type", "text/html; charset=UTF-8");
        req.response.write("<html><body><h1>Hello from node.x!</h1></body></html>", "UTF-8").end();
      }
    }).setSSL(true).setKeyStorePath("server-keystore.jks").setKeyStorePassword("wibble").listen(4443);
  }
}
