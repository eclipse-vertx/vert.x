package org.nodex.examples.websockets;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;
import org.nodex.core.http.Websocket;
import org.nodex.core.http.WebsocketConnectHandler;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 08:05
 */
public class WebsocketsExample {
  public static void main(String[] args) throws Exception {
    HttpServer server = new HttpServer();

    server.websocketHandler(new WebsocketConnectHandler() {
      public boolean onConnect(final Websocket ws) {
        ws.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            ws.writeTextFrame(data.toString()); // Echo it back
          }
        });
        System.out.println("uri is " + ws.uri);
        return ws.uri.equals("/myapp"); // Only accept connections on path /myapp
      }
    }).requestHandler(new HttpRequestHandler() {
      public void onRequest(HttpServerRequest req, HttpServerResponse resp) {
        if (req.path.equals("/")) resp.sendFile("ws.html"); // Serve the html
      }
    }).listen(8080);

    System.out.println("Any key to exit");
    System.in.read();

    server.close();
  }
}
