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

package org.nodex.java.examples.upload;

import org.nodex.java.core.Completion;
import org.nodex.java.core.CompletionHandler;
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.NodexMain;
import org.nodex.java.core.SimpleEventHandler;
import org.nodex.java.core.file.AsyncFile;
import org.nodex.java.core.file.FileSystem;
import org.nodex.java.core.http.HttpServer;
import org.nodex.java.core.http.HttpServerRequest;
import org.nodex.java.core.streams.Pump;

import java.util.UUID;

public class UploadServer extends NodexMain {
  public static void main(String[] args) throws Exception {
    new UploadServer().run();

    System.out.println("Hit enter to exit");
    System.in.read();
  }

  public void go() throws Exception {

    new HttpServer().requestHandler(new EventHandler<HttpServerRequest>() {
      public void onEvent(final HttpServerRequest req) {

        // We first pause the request so we don't receive any data between now and when the file is opened
        req.pause();

        final String filename = "file-" + UUID.randomUUID().toString() + ".upload";

        FileSystem.instance.open(filename, new CompletionHandler<AsyncFile>() {
          public void onEvent(Completion<AsyncFile> completion) {
            final AsyncFile file = completion.result;
            final Pump pump = new Pump(req, file.getWriteStream());
            final long start = System.currentTimeMillis();
            req.endHandler(new SimpleEventHandler() {
              public void onEvent() {
                file.close(new CompletionHandler<Void>() {
                  public void onEvent(Completion<Void> completion) {
                    if (completion.succeeded()) {
                      req.response.end();
                      long end = System.currentTimeMillis();
                      System.out.println("Uploaded " + pump.getBytesPumped() + " bytes to " + filename + " in " + (end - start) + " ms");
                    } else {
                      completion.exception.printStackTrace(System.err);
                    }
                  }
                });
              }
            });
            pump.start();
            req.resume();
          }
        });
      }
    }).listen(8080);
  }
}
