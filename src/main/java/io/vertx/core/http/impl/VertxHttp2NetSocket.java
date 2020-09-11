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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.nio.charset.Charset;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2NetSocket<C extends Http2ConnectionBase> extends VertxHttp2Stream<C> implements NetSocket {

  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Buffer> dataHandler;
  private Handler<Void> drainHandler;

  public VertxHttp2NetSocket(C conn, ContextInternal context) {
    super(conn, context);
  }

  // Stream impl

  @Override
  void handleEnd(MultiMap trailers) {
    try {
      Handler<Void> handler = endHandler();
      if (handler != null) {
        // Give opportunity to send a last chunk
        handler.handle(null);
      }
    } finally {
      end();
    }
  }

  @Override
  void handleData(Buffer buf) {
    Handler<Buffer> handler = handler();
    if (handler != null) {
      handler.handle(buf);
    }
  }

  @Override
  void handleReset(long errorCode) {
    handleException(new StreamResetException(errorCode));
  }

  @Override
  void handleException(Throwable cause) {
    Handler<Throwable> handler = exceptionHandler();
    if (handler != null) {
      handler.handle(cause);
    }
  }

  @Override
  void handleClose() {
    super.handleClose();
    Handler<Void> handler = closeHandler();
    if (handler != null) {
      handler.handle(null);
    }
  }

  @Override
  void handleWritabilityChanged(boolean writable) {
    Handler<Void> handler = drainHandler();
    if (handler != null && writable) {
      handler.handle(null);
    }
  }

  @Override
  void handlePriorityChange(StreamPriority newPriority) {
  }

// NetSocket impl

  @Override
  public NetSocket exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      exceptionHandler = handler;
    }
    return this;
  }

  Handler<Throwable> exceptionHandler() {
    synchronized (conn) {
      return exceptionHandler;
    }
  }

  @Override
  public NetSocket handler(Handler<Buffer> handler) {
    synchronized (conn) {
      dataHandler = handler;
    }
    return this;
  }

  Handler<Buffer> handler() {
    synchronized (conn) {
      return dataHandler;
    }
  }

  @Override
  public NetSocket fetch(long amount) {
    doFetch(amount);
    return this;
  }

  @Override
  public NetSocket pause() {
    doPause();
    return this;
  }

  @Override
  public NetSocket resume() {
    return fetch(Long.MAX_VALUE);
  }

  @Override
  public NetSocket endHandler(Handler<Void> handler) {
    synchronized (conn) {
      endHandler = handler;
    }
    return this;
  }

  Handler<Void> endHandler() {
    synchronized (conn) {
      return endHandler;
    }
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public NetSocket drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      drainHandler = handler;
    }
    return this;
  }

  Handler<Void> drainHandler() {
    synchronized (conn) {
      return drainHandler;
    }
  }

  @Override
  public boolean writeQueueFull() {
    return isNotWritable();
  }

  @Override
  public String writeHandlerID() {
    // TODO
    return null;
  }

  private Future<Void> write(ByteBuf data, boolean end) {
    Promise<Void> promise = context.promise();
    writeData(data, end, promise);
    return promise.future();
  }

  @Override
  public Future<Void> write(Buffer data) {
    return write(data.getByteBuf(), false);
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = write(data);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> write(String str, String enc) {
    Charset cs = enc != null ? Charset.forName(enc) : CharsetUtil.UTF_8;
    return write(Unpooled.copiedBuffer(str, cs), false);
  }

  @Override
  public void write(String str, String enc, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = write(str, enc);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> write(String str) {
    return write(str, (String) null);
  }

  @Override
  public void write(String str, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = write(str);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> end(Buffer data) {
    return write(data.getByteBuf(), true);
  }

  @Override
  public void end(Buffer buffer, Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = end(buffer);
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> end() {
    return write(Unpooled.EMPTY_BUFFER, true);
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    Future<Void> fut = end();
    if (handler != null) {
      fut.onComplete(handler);
    }
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    Promise<Void> promise = context.promise();
    sendFile(filename, offset, length, promise);
    return promise.future();
  }

  @Override
  public NetSocket sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {

    Handler<AsyncResult<Void>> h;
    if (resultHandler != null) {
      Context resultCtx = vertx.getOrCreateContext();
      h = ar -> {
        resultCtx.runOnContext((v) -> {
          resultHandler.handle(ar);
        });
      };
    } else {
      h = ar -> {};
    }
    resolveFile(filename, offset, length, ar -> {
      if (ar.succeeded()) {
        AsyncFile file = ar.result();
        file.pipeTo(this, ar1 -> file.close(ar2 -> {
          Throwable failure = ar1.failed() ? ar1.cause() : ar2.failed() ? ar2.cause() : null;
          if(failure == null)
            h.handle(ar1);
          else
            h.handle(Future.failedFuture(failure));
        }));
      } else {
        h.handle(ar.mapEmpty());
      }
    });
    return this;
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public Future<Void> close() {
    return end();
  }

  @Override
  public void close(Handler<AsyncResult<Void>> handler) {
    end(handler);
  }

  @Override
  public NetSocket closeHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      closeHandler = handler;
    }
    return this;
  }

  Handler<Void> closeHandler() {
    synchronized (conn) {
      return closeHandler;
    }
  }

  @Override
  public NetSocket upgradeToSsl(Handler<AsyncResult<Void>> handler) {
    throw new UnsupportedOperationException("Cannot upgrade HTTP/2 stream to SSL");
  }

  @Override
  public NetSocket upgradeToSsl(String serverName, Handler<AsyncResult<Void>> handler) {
    throw new UnsupportedOperationException("Cannot upgrade HTTP/2 stream to SSL");
  }

  @Override
  public Future<Void> upgradeToSsl() {
    return Future.failedFuture("Cannot upgrade HTTP/2 stream to SSL");
  }

  @Override
  public Future<Void> upgradeToSsl(String serverName) {
    return Future.failedFuture("Cannot upgrade HTTP/2 stream to SSL");
  }

  @Override
  public boolean isSsl() {
    return conn.isSsl();
  }

  @Override
  public SSLSession sslSession() {
    return conn.sslSession();
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.peerCertificateChain();
  }

  @Override
  public String indicatedServerName() {
    return conn.indicatedServerName();
  }
}
