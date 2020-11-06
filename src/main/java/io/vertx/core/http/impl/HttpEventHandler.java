/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
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
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextInternal;

/**
 * All HTTP event related handlers.
 */
class HttpEventHandler {

  final ContextInternal context;
  private Handler<Buffer> chunkHandler;
  private Handler<Void> endHandler;
  private Handler<Throwable> exceptionHandler;
  private Buffer body;
  private Promise<Buffer> bodyPromise;
  private Promise<Void> endPromise;

  HttpEventHandler(ContextInternal context) {
    this.context = context;
  }

  void chunkHandler(Handler<Buffer> handler) {
    chunkHandler = handler;
  }

  void endHandler(Handler<Void> handler) {
    endHandler = handler;
  }

  void exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
  }

  void handleChunk(Buffer chunk) {
    Handler<Buffer> handler = chunkHandler;
    if (handler != null) {
      context.dispatch(chunk, handler);
    }
    if (body != null) {
      body.appendBuffer(chunk);
    }
  }

  Future<Buffer> body() {
    if (body == null) {
      body = Buffer.buffer();
      bodyPromise = context.promise();
    }
    return bodyPromise.future();
  }

  Future<Void> end() {
    if (endPromise == null) {
      endPromise = context.promise();
    }
    return endPromise.future();
  }

  void handleEnd() {
    Handler<Void> handler = endHandler;
    if (handler != null) {
      context.dispatch(handler);
    }
    if (bodyPromise != null) {
      bodyPromise.tryComplete(body);
    }
    if (endPromise != null) {
      endPromise.tryComplete();
    }
  }

  void handleException(Throwable err) {
    Handler<Throwable> handler = exceptionHandler;
    if (handler != null) {
      context.dispatch(err, handler);
    }
    if (bodyPromise != null) {
      bodyPromise.tryFail(err);
    }
    if (endPromise != null) {
      endPromise.tryFail(err);
    }
  }
}
