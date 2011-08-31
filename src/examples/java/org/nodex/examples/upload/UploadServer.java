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

package org.nodex.examples.upload;

import org.nodex.core.CompletionHandler;
import org.nodex.core.NodexMain;
import org.nodex.core.file.AsyncFile;
import org.nodex.core.file.FileSystem;
import org.nodex.core.http.HttpRequestHandler;
import org.nodex.core.http.HttpServer;
import org.nodex.core.http.HttpServerRequest;
import org.nodex.core.streams.Pump;

import java.util.UUID;

public class UploadServer extends NodexMain {
  public static void main(String[] args) throws Exception {
    new UploadServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {

    new HttpServer(new HttpRequestHandler() {
      public void onRequest(final HttpServerRequest req) {

        // We first pause the request so we don't receive any data between now and when the file is opened
        req.pause();

        final String filename = "file-" + UUID.randomUUID().toString() + ".upload";

        FileSystem.instance.open(filename, new CompletionHandler<AsyncFile>() {
          public void onCompletion(final AsyncFile file) {
            req.endHandler(new Runnable() {
              public void run() {
                file.close(new CompletionHandler<Void>() {
                  public void onCompletion(Void v) {
                    req.response.end();
                    System.out.println("Uploaded data to " + filename);
                  }
                  public void onException(Exception e) {
                    e.printStackTrace(System.err);
                  }
                });
              }
            });
            Pump pump = new Pump(req, file.getWriteStream());
            pump.start();
            req.resume();
          }

          public void onException(Exception e) {
            e.printStackTrace();
          }
        });
      }
    }).listen(8080);
  }
}
