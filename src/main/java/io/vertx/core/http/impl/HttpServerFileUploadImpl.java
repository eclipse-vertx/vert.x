/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;

import java.nio.charset.Charset;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class HttpServerFileUploadImpl implements HttpServerFileUpload {

  private final ReadStream<Buffer> stream;
  private final Context context;
  private final String name;
  private final String filename;
  private final String contentType;
  private final String contentTransferEncoding;
  private final Charset charset;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private AsyncFile file;
  private Handler<Throwable> exceptionHandler;

  private long size;
  private boolean lazyCalculateSize;

  HttpServerFileUploadImpl(Context context, ReadStream<Buffer> stream, String name, String filename, String contentType,
                           String contentTransferEncoding,
                           Charset charset, long size) {
    this.context = context;
    this.stream = stream;
    this.name = name;
    this.filename = filename;
    this.contentType = contentType;
    this.contentTransferEncoding = contentTransferEncoding;
    this.charset = charset;
    this.size = size;
    this.lazyCalculateSize = size == 0;

    stream.handler(this::handleData);
    stream.endHandler(v -> this.handleEnd());
  }

  private void handleData(Buffer data) {
    Handler<Buffer> h;
    synchronized (HttpServerFileUploadImpl.this) {
      h = dataHandler;
      size += data.length();
    }
    if (h != null) {
      h.handle(data);
    }
  }

  private void handleEnd() {
    Handler<Void> handler;
    synchronized (this) {
      lazyCalculateSize = false;
      handler = endHandler;
    }
    if (handler != null) {
      handler.handle(null);
    }
  }

  private void notifyExceptionHandler(Throwable cause) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(cause);
    }
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
  public synchronized long size() {
    return size;
  }

  @Override
  public synchronized HttpServerFileUpload handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  @Override
  public HttpServerFileUpload pause() {
    stream.pause();
    return this;
  }

  @Override
  public HttpServerFileUpload fetch(long amount) {
    stream.fetch(amount);
    return this;
  }

  @Override
  public HttpServerFileUpload resume() {
    stream.resume();
    return this;
  }

  @Override
  public synchronized HttpServerFileUpload exceptionHandler(Handler<Throwable> exceptionHandler) {
    this.exceptionHandler = exceptionHandler;
    return this;
  }

  @Override
  public synchronized HttpServerFileUpload endHandler(Handler<Void> handler) {
    this.endHandler = handler;
    return this;
  }

  @Override
  public HttpServerFileUpload streamToFileSystem(String filename) {
    Pipe<Buffer> pipe = stream.pipe().endOnComplete(false);
    context.owner().fileSystem().open(filename, new OpenOptions(), ar -> {
      if (ar.succeeded()) {
        file =  ar.result();
        pipe.to(file, ar2 -> {
          file.close(ar3 -> {
            Throwable failure = ar2.failed() ? ar2.cause() : ar3.failed() ? ar3.cause() : null;
            if (failure != null) {
              notifyExceptionHandler(failure);
            }
            synchronized (HttpServerFileUploadImpl.this) {
              size = file.getWritePos();
            }
            handleEnd();
          });
        });
      } else {
        pipe.close();
        notifyExceptionHandler(ar.cause());
      }
    });
    return this;
  }

  @Override
  public synchronized boolean isSizeAvailable() {
    return !lazyCalculateSize;
  }

  @Override
  public synchronized AsyncFile file() {
    return file;
  }
}
