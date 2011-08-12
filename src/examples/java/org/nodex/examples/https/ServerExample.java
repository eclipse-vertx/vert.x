package org.nodex.examples.https;

import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;

public class ServerExample {
  public static void main(String[] args) throws Exception {
    HttpServer server = new HttpServer(new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.requestHandler(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req, HttpServerResponse resp) {
            System.out.println("Got request: " + req.uri);
            System.out.println("Headers are: ");
            for (String key : req.getHeaderNames()) {
              System.out.println(key + ":" + req.getHeader(key));
            }
            resp.putHeader("Content-Type", "text/html; charset=UTF-8");
            resp.write("<html><body><h1>Hello from node.x!</h1></body></html>", "UTF-8").end();
          }
        });
      }
    }).setSSL(true).setKeyStorePath("server-keystore.jks").setKeyStorePassword("wibble").listen(4443);

    System.out.println("Any key to exit");
    System.in.read();

    server.close();
  }
}
