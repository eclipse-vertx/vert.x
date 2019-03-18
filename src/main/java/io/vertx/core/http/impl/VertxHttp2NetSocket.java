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

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.CharsetUtil;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
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

  public VertxHttp2NetSocket(C conn, ContextInternal context, Http2Stream stream, boolean writable) {
    super(conn, context, stream, writable);
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
  void handleInterestedOpsChanged() {
    Handler<Void> handler = drainHandler();
    if (handler != null && !writeQueueFull()) {
      handler.handle(null);
    }
  }

  @Override
  void handlePriorityChange(StreamPriority streamPriority) {
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
  public Future<Void> write(Buffer data) {
    synchronized (conn) {
      Promise<Void> promise = Promise.promise();
      writeData(data.getByteBuf(), false, promise);
      return promise.future();
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

  @Override
  public Future<Void> write(String str) {
    Promise<Void> promise = Promise.promise();
    write(str, null, promise);
    return promise.future();
  }

  @Override
  public void write(String str, Handler<AsyncResult<Void>> handler) {
    write(str, null, handler);
  }

  @Override
  public Future<Void> write(String str, String enc) {
    Promise<Void> promise = Promise.promise();
    write(str, enc, promise);
    return promise.future();
  }

  @Override
  public void write(String str, String enc, Handler<AsyncResult<Void>> handler) {
    Charset cs = enc != null ? Charset.forName(enc) : CharsetUtil.UTF_8;
    writeData(Unpooled.copiedBuffer(str, cs), false, handler);
  }

  @Override
  public void write(Buffer message, Handler<AsyncResult<Void>> handler) {
    conn.handler.writeData(stream, message.getByteBuf(), false, handler);
  }

  @Override
  public NetSocket sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    synchronized (conn) {
      Context resultCtx = resultHandler != null ? vertx.getOrCreateContext() : null;

      File file = vertx.resolveFile(filename);
      if (!file.exists()) {
        if (resultHandler != null) {
          resultCtx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(new FileNotFoundException())));
        } else {
          // log.error("File not found: " + filename);
        }
        return this;
      }

      RandomAccessFile raf;
      try {
        raf = new RandomAccessFile(file, "r");
      } catch (IOException e) {
        if (resultHandler != null) {
          resultCtx.runOnContext((v) -> resultHandler.handle(Future.failedFuture(e)));
        } else {
          //log.error("Failed to send file", e);
        }
        return this;
      }

      long contentLength = Math.min(length, file.length() - offset);

      Promise<Long> result = Promise.promise();
      result.future().setHandler(ar -> {
        if (resultHandler != null) {
          resultCtx.runOnContext(v -> {
            resultHandler.handle(Future.succeededFuture());
          });
        }
      });

      FileStreamChannel fileChannel = new FileStreamChannel(result, this, offset, contentLength);
      drainHandler(fileChannel.drainHandler);
      handlerContext.channel()
        .eventLoop()
        .register(fileChannel)
        .addListener((ChannelFutureListener) future -> {
          if (future.isSuccess()) {
            fileChannel.pipeline().fireUserEventTriggered(raf);
          } else {
            result.tryFail(future.cause());
          }
        });
    }
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
  public Future<Void> end() {
    Promise<Void> promise = Promise.promise();
    end(promise);
    return promise.future();
  }

  @Override
  public void end(Buffer buffer, Handler<AsyncResult<Void>> handler) {
    writeData(buffer.getByteBuf(), true, handler);
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    writeData(Unpooled.EMPTY_BUFFER, true, handler);
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
