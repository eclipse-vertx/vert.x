package org.nodex.java.examples.proxy;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.http.HttpClientRequest;
import org.nodex.java.core.http.HttpClientResponse;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 11:44
 */
public class HttpClient extends NodexMain {
  public static void main(String[] args) throws Exception {
    new HttpClient().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    HttpClientRequest req = new org.nodex.java.core.http.HttpClient().setPort(8080).setHost("localhost").put("/some-url", new EventHandler<HttpClientResponse>() {
      public void onEvent(HttpClientResponse response) {
        response.dataHandler(new EventHandler<Buffer>() {
          public void onEvent(Buffer data) {
            System.out.println("Got response data:" + data);
          }
        });
      }
    });
    //Write a few chunks
    req.setChunked(true);
    for (int i = 0; i < 10; i++) {
      req.write("client-data-chunk-" + i);
    }
    req.end();
  }
}
