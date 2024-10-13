/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.FileSystem;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;

import java.nio.charset.Charset;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
class HttpServerFileUploadImpl implements HttpServerFileUpload {

  private final ReadStream<Buffer> stream;
  private final ContextInternal context;
  private final String name;
  private final String filename;
  private final String contentType;
  private final String contentTransferEncoding;
  private final Charset charset;

  private Handler<Buffer> dataHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;

  private long size;
  private boolean lazyCalculateSize;
  private AsyncFile file;
  private Pipe<Buffer> pipe;
  private boolean cancelled;

  HttpServerFileUploadImpl(ContextInternal context, ReadStream<Buffer> stream, String name, String filename, String contentType,
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
    stream.exceptionHandler(this::handleException);
    stream.endHandler(v -> this.handleEnd());
  }

  private void handleData(Buffer data) {
    Handler<Buffer> handler;
    synchronized (HttpServerFileUploadImpl.this) {
      handler = dataHandler;
      if (lazyCalculateSize) {
        size += data.length();
      }
    }
    if (handler != null) {
      context.dispatch(data, handler);
    }
  }

  private void handleException(Throwable cause) {
    Handler<Throwable> handler;
    synchronized (this) {
      handler = exceptionHandler;
    }
    if (handler != null) {
      context.dispatch(cause, handler);
    }
  }

  private void handleEnd() {
    Handler<Void> handler;
    synchronized (this) {
      lazyCalculateSize = false;
      handler = endHandler;
    }
    if (handler != null) {
      context.dispatch(handler);
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
  public Future<Void> streamToFileSystem(String filename) {
    synchronized (this) {
      if (pipe != null) {
        return context.failedFuture("Already streaming");
      }
      pipe = pipe().endOnComplete(true);
    }
    FileSystem fs = context.owner().fileSystem();
    Future<AsyncFile> fut = fs.open(filename, new OpenOptions());
    fut.onFailure(err -> {
      pipe.close();
    });
    return fut.compose(f -> {
      Future<Void> to = pipe.to(f);
      return to.compose(v -> {
        synchronized (HttpServerFileUploadImpl.this) {
          if (!cancelled) {
            file = f;
            return context.succeededFuture();
          }
          fs.delete(filename);
          return context.failedFuture("Streaming aborted");
        }
      }, err -> {
        fs.delete(filename);
        return context.failedFuture(err);
      });
    });
  }

  @Override
  public boolean cancelStreamToFileSystem() {
    synchronized (this) {
      if (pipe == null) {
        throw new IllegalStateException("Not a streaming upload");
      }
      if (file != null) {
        return false;
      }
      cancelled = true;
    }
    pipe.close();
    return true;
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
