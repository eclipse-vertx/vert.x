package org.nodex.examples.https;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpClientConnectHandler;
import org.nodex.core.http.HttpClientConnection;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpResponseHandler;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 11:44
 */
public class ClientExample {
  public static void main(String[] args) throws Exception {

    HttpClient client = new HttpClient().setSSL(true).setTrustAll(true).connect(4443, "localhost", new HttpClientConnectHandler() {
      public void onConnect(final HttpClientConnection conn) {
        conn.getNow("/", new HttpResponseHandler() {
          public void onResponse(HttpClientResponse response) {
            response.dataHandler(new DataHandler() {
              public void onData(Buffer data) {
                System.out.println(data);
              }
            });
          }
        });
      }
    });

    System.out.println("Any key to exit");
    System.in.read();

    client.close();
  }
}
