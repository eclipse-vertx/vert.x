/*
 * Copyright 2011 VMware, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.java.examples.proxy;

import org.nodex.java.core.Handler;
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
    HttpClientRequest req = new org.nodex.java.core.http.HttpClient().setPort(8080).setHost("localhost").put("/some-url", new Handler<HttpClientResponse>() {
      public void handle(HttpClientResponse response) {
        response.dataHandler(new Handler<Buffer>() {
          public void handle(Buffer data) {
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
