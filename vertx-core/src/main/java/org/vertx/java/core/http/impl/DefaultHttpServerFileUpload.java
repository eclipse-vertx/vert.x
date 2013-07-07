/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.java.core.http.impl;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.AsyncFile;
import org.vertx.java.core.http.HttpServerFileUpload;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.streams.Pump;

import java.nio.charset.Charset;

/**
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class DefaultHttpServerFileUpload implements HttpServerFileUpload {

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private AsyncFile file;
  private Handler<Throwable> exceptionHandler;

  private final HttpServerRequest req;
  private final Vertx vertx;

  private final String name;
  private final String filename;
  private final String contentType;
  private final String contentTransferEncoding;
  private final Charset charset;
  private final long size;

  private boolean paused;
  private Buffer pauseBuff;
  private boolean complete;

  DefaultHttpServerFileUpload(Vertx vertx, HttpServerRequest req, String name, String filename, String contentType,
                              String contentTransferEncoding,
                              Charset charset, long size) {
    this.vertx = vertx;
    this.req = req;
    this.name = name;
    this.filename = filename;
    this.contentType = contentType;
    this.contentTransferEncoding = contentTransferEncoding;
    this.charset = charset;
    this.size = size;
  }

  @Override
  public String filename() {
    return filename;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public String contentType() {
    return contentType;
  }

  @Override
  public String contentTransferEncoding() {
    return contentTransferEncoding;
  }

  @Override
  public Charset charset() {
    return charset;
  }

  @Override
  public long size() {
    return size;
  }

  @Override
  public HttpServerFileUpload dataHandler(Handler<Buffer> handler) {
    this.dataHandler = handler;
    return this;
  }

  @Override
  public HttpServerFileUpload pause() {
    req.pause();
    paused = true;
    return this;
  }

  @Override
  public HttpServerFileUpload resume() {
    if (paused) {
      req.resume();
      paused = false;
      if (pauseBuff != null) {
        receiveData(pauseBuff);
        pauseBuff = null;
      }
      if (complete && endHandler != null) {
        endHandler.handle(null);
      }
    }
    return this;
  }

  @Override
  public HttpServerFileUpload exceptionHandler(Handler<Throwable> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  @Override
  public HttpServerFileUpload endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public HttpServerFileUpload streamToFileSystem(String filename) {
    pause();
    vertx.fileSystem().open(filename, new AsyncResultHandler<AsyncFile>() {
      public void handle(final AsyncResult<AsyncFile> ar) {
        if (ar.succeeded()) {
          file =  ar.result();

          Pump p = Pump.createPump(DefaultHttpServerFileUpload.this, ar.result());
          p.start();

          resume();
        } else {
          notifyExceptionHandler(ar.cause());
        }
      }
    });
    return this;
  }

  void receiveData(Buffer data) {
    try {
      if (!paused) {
        if (dataHandler != null) {
          dataHandler.handle(data);
        }
      } else {
        if (pauseBuff == null) {
          pauseBuff = new Buffer();
        }
        pauseBuff.appendBuffer(data);
      }
    } finally {
      // release the underlying buffer
      // See https://github.com/vert-x/vert.x/issues/677
      data.getByteBuf().release();
    }
  }

  void complete() {
    if (paused) {
      complete = true;
    } else {
      if (file == null) {
        notifyEndHandler();
      } else {
        file.close(new AsyncResultHandler<Void>() {
          @Override
          public void handle(AsyncResult<Void> event) {
            if (event.failed()) {
              notifyExceptionHandler(event.cause());
            }
            notifyEndHandler();
          }
        });
      }

    }
  }

  private void notifyEndHandler() {
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  private void notifyExceptionHandler(Throwable cause) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(cause);
    }
  }
}
