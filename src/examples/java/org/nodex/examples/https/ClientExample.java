package org.nodex.examples.https;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpResponseHandler;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 11:44
 */
public class ClientExample {
  public static void main(String[] args) throws Exception {

    HttpClient client = new HttpClient().setSSL(true).setTrustAll(true).setPort(4443).setHost("localhost");
    client.getNow("/", new HttpResponseHandler() {
      public void onResponse(HttpClientResponse response) {
        response.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            System.out.println(data);
          }
        });
      }
    });
    System.out.println("Any key to exit");
    System.in.read();

    client.close();
  }
}
