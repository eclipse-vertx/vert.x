/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.java.examples.proxy;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleEventHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.http.HttpClient;
import org.nodex.java.core.http.HttpClientRequest;
import org.nodex.java.core.http.HttpClientResponse;
import org.nodex.java.core.http.HttpServer;
import org.nodex.java.core.http.HttpServerRequest;

public class ProxyServer extends NodexMain {
  public static void main(String[] args) throws Exception {
    new ProxyServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {

    final HttpClient client = new HttpClient().setHost("localhost").setPort(8282);

    new HttpServer().requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(final HttpServerRequest req) {
        System.out.println("Proxying request: " + req.uri);
        final HttpClientRequest cReq = client.request(req.method, req.uri, new EventHandler<HttpClientResponse>() {
          public void onEvent(HttpClientResponse cRes) {
            System.out.println("Proxying response: " + cRes.statusCode);
            req.response.statusCode = cRes.statusCode;
            req.response.putAllHeaders(cRes.getHeaders());
            req.response.setChunked(true);
            cRes.dataHandler(new EventHandler<Buffer>() {
              public void onEvent(Buffer data) {
                System.out.println("Proxying response body:" + data);
                req.response.write(data);
              }
            });
            cRes.endHandler(new SimpleEventHandler() {
              public void onEvent() {
                req.response.end();
              }
            });
          }
        });
        cReq.putAllHeaders(req.getHeaders());
        cReq.setChunked(true);
        req.dataHandler(new EventHandler<Buffer>() {
          public void onEvent(Buffer data) {
            System.out.println("Proxying request body:" + data);
            cReq.write(data);
          }
        });
        req.endHandler(new SimpleEventHandler() {
          public void onEvent() {
            cReq.end();
          }
        });
      }
    }).listen(8080);
  }
}
