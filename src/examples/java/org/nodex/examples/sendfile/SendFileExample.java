package org.nodex.examples.sendfile;

import org.nodex.core.NodexMain;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 09:04
 */
public class SendFileExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new SendFileExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    //Here is the web server!
    new HttpServer(new HttpRequestHandler() {
      public void onRequest(HttpServerRequest req) {
        if (req.path.equals("/")) {
          req.response.sendFile("index.html");
        } else {
          //Clearly in a real server you would check the path for better security!!
          req.response.sendFile("." + req.path);
        }
      }
    }).listen(8080);

  }
}
