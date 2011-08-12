package org.nodex.examples.sendfile;

import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 09:04
 */
public class SendFileExample {
  public static void main(String[] args) throws Exception {
    HttpServer server = new HttpServer(new HttpServerConnectHandler() {
      public void onConnect(final HttpServerConnection conn) {
        conn.requestHandler(new HttpRequestHandler() {
          public void onRequest(HttpServerRequest req, HttpServerResponse resp) {
            if (req.path.equals("/")) {
              resp.sendFile("index.html");
            } else {
              //Clearly in a real server you would check the path for better security!!
              resp.sendFile("." + req.path);
            }
          }
        });
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.close();
  }
}
