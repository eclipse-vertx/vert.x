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

package org.nodex.java.examples.sendfile;

import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.http.HttpServer;
import org.nodex.java.core.http.HttpServerRequest;

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

  private static final String webroot = "sendfile/";

  public void go() throws Exception {
    //Here is the web server!
    new HttpServer().requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(HttpServerRequest req) {
        if (req.path.equals("/")) {
          req.response.sendFile(webroot + "index.html");
        } else {
          //Clearly in a real server you would check the path for better security!!
          req.response.sendFile(webroot + req.path);
        }
      }
    }).listen(8080);

  }
}
