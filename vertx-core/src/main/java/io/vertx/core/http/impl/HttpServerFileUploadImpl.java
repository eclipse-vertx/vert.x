/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.http.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.AsyncResultHandler;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.streams.Pump;

import java.nio.charset.Charset;

/**
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class HttpServerFileUploadImpl implements HttpServerFileUpload {

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
  private long size;

  private boolean paused;
  private Buffer pauseBuff;
  private boolean complete;

  HttpServerFileUploadImpl(Vertx vertx, HttpServerRequest req, String name, String filename, String contentType,
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
  public String charset() {
    return charset.toString();
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
    vertx.fileSystem().open(filename, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        file =  ar.result();

        Pump p = Pump.createPump(HttpServerFileUploadImpl.this, ar.result());
        p.start();

        resume();
      } else {
        notifyExceptionHandler(ar.cause());
      }
    });
    return this;
  }

  void receiveData(Buffer data) {
    if (!paused) {
      size += data.length();
      if (dataHandler != null) {
        dataHandler.handle(data);
      }
    } else {
      if (pauseBuff == null) {
        pauseBuff = Buffer.newBuffer();
      }
      pauseBuff.appendBuffer(data);
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
