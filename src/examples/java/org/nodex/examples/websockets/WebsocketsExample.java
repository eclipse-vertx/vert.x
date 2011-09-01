package org.nodex.examples.websockets;

import org.nodex.core.EventHandler;
import org.nodex.core.NodexMain;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.Websocket;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 08:05
 */
public class WebsocketsExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new WebsocketsExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new HttpServer().websocketHandler(new EventHandler<Websocket>() {
      public void onEvent(final Websocket ws) {
        if (ws.uri.equals("/myapp")) {
          ws.dataHandler(new EventHandler<Buffer>() {
            public void onEvent(Buffer data) {
              ws.writeTextFrame(data.toString()); // Echo it back
            }
          });
        } else {
          //Reject it
          ws.close();
        }
      }
    }).requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(HttpServerRequest req) {
        if (req.path.equals("/")) req.response.sendFile("ws.html"); // Serve the html
      }
    }).listen(8080);
  }
}
