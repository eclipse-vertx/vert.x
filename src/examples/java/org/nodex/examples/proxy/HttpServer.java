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

package org.nodex.examples.proxy;

import org.nodex.core.NodexMain;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.buffer.DataHandler;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServerRequest;

public class HttpServer extends NodexMain {
  public static void main(String[] args) throws Exception {
    new HttpServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {
    new org.nodex.core.http.HttpServer(new HttpRequestHandler() {
      public void onRequest(final HttpServerRequest req) {
        System.out.println("Got request: " + req.uri);
        System.out.println("Headers are: ");
        for (String key : req.getHeaderNames()) {
          System.out.println(key + ":" + req.getHeader(key));
        }
        req.dataHandler(new DataHandler() {
          public void onData(Buffer data) {
            System.out.println("Got request body: " + data);
          }
        });
        req.endHandler(new Runnable() {
          public void run() {
            //Now we got everything, send back some data
            for (int i = 0; i < 10; i++) {
              req.response.write("server-data-chunk-" + i);
            }
            req.response.end();
          }
        });
      }
    }).listen(8282);
  }
}
