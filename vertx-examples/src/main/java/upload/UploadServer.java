package upload;

/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.streams.Pump;
import org.vertx.java.platform.Verticle;

import java.util.UUID;

public class UploadServer extends Verticle {

  public void start() {

    vertx.createHttpServer().requestHandler(new Handler<HttpServerRequest>() {
      public void handle(final HttpServerRequest req) {

        // We first pause the request so we don't receive any data between now and when the file is opened
        req.pause();

        final String filename = "upload/file-" + UUID.randomUUID().toString() + ".upload";

        vertx.fileSystem().open(filename, new AsyncResultHandler<AsyncFile>() {
          public void handle(AsyncResult<AsyncFile> ar) {
            final AsyncFile file = ar.result;
            final Pump pump = Pump.createPump(req, file.getWriteStream());
            final long start = System.currentTimeMillis();
            req.endHandler(new SimpleHandler() {
              public void handle() {
                file.close(new AsyncResultHandler<Void>() {
                  public void handle(AsyncResult<Void> ar) {
                    if (ar.exception == null) {
                      req.response.end();
                      long end = System.currentTimeMillis();
                      System.out.println("Uploaded " + pump.getBytesPumped() + " bytes to " + filename + " in " + (end - start) + " ms");
                    } else {
                      ar.exception.printStackTrace(System.err);
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
