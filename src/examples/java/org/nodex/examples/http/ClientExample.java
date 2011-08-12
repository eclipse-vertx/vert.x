package org.nodex.examples.http;

import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpClient;
import org.nodex.core.http.HttpClientConnectHandler;
import org.nodex.core.http.HttpClientConnection;
import org.nodex.core.http.HttpClientResponse;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpResponseHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerConnectHandler;
import org.nodex.core.http.HttpServerConnection;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.http.HttpServerResponse;

/**
 * User: tim
 * Date: 12/08/11
 * Time: 11:44
 */
public class ClientExample {
  public static void main(String[] args) throws Exception {

    HttpClient client = new HttpClient().connect(8080, "localhost", new HttpClientConnectHandler() {
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
