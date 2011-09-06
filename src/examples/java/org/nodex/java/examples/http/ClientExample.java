package org.nodex.java.examples.http;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.http.HttpClient;
import org.nodex.java.core.http.HttpClientResponse;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 11:44
 */
public class ClientExample extends NodexMain {
  public static void main(String[] args) throws Exception {
    new ClientExample().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new HttpClient().setPort(8080).setHost("localhost").getNow("/", new EventHandler<HttpClientResponse>() {
      public void onEvent(HttpClientResponse response) {
        response.dataHandler(new EventHandler<Buffer>() {
          public void onEvent(Buffer data) {
            System.out.println(data);
          }
        });
      }
    });
  }
}
