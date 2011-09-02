package org.nodex.examples.proxy;

import org.nodex.core.EventHandler;
import org.nodex.core.NodexMain;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.http.HttpClientRequest;
import org.nodex.core.http.HttpClientResponse;

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
    HttpClientRequest req = new org.nodex.core.http.HttpClient().setPort(8080).setHost("localhost").put("/some-url", new EventHandler<HttpClientResponse>() {
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
