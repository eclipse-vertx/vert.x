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

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.nio.channels.ClosedChannelException;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class HttpNetSocket implements NetSocket {

  static HttpNetSocket netSocket(ConnectionBase conn, ContextInternal context, ReadStream<Buffer> readStream, WriteStream<Buffer> writeStream) {
    HttpNetSocket sock = new HttpNetSocket(conn, context, readStream, writeStream);
    readStream.handler(sock::handleData);
    readStream.endHandler(sock::handleEnd);
    readStream.exceptionHandler(sock::handleException);
    return sock;
  }

  private final ConnectionBase conn;
  private final ContextInternal context;
  private final ReadStream<Buffer> readStream;
  private final WriteStream<Buffer> writeStream;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Buffer> dataHandler;

  private HttpNetSocket(ConnectionBase conn, ContextInternal context, ReadStream<Buffer> readStream, WriteStream<Buffer> writeStream) {
    this.conn = conn;
    this.context = context;
    this.readStream = readStream;
    this.writeStream = writeStream;
  }

  private void handleEnd(Void v) {
    Handler<Void> endHandler = endHandler();
    if (endHandler != null) {
      // Give opportunity to send a last chunk
      endHandler.handle(null);
    }
    Handler<Void> closeHandler = closeHandler();
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  private void handleData(Buffer buf) {
    Handler<Buffer> handler = handler();
    if (handler != null) {
      handler.handle(buf);
    }
  }

  private void handleException(Throwable cause) {
    if (cause == ConnectionBase.CLOSED_EXCEPTION || cause.getClass() == ClosedChannelException.class) {
      Handler<Void> endHandler = endHandler();
      if (endHandler != null) {
        endHandler.handle(null);
      }
      Handler<Void> closeHandler = closeHandler();
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
    } else {
      Handler<Throwable> handler = exceptionHandler();
      if (handler != null) {
        handler.handle(cause);
      }
    }
  }

  @Override
  public synchronized NetSocket exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  @Override
  public synchronized NetSocket handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  synchronized Handler<Buffer> handler() {
    return dataHandler;
  }

  @Override
  public NetSocket fetch(long amount) {
    readStream.fetch(amount);
    return this;
  }

  @Override
  public NetSocket pause() {
    readStream.pause();
    return this;
  }

  @Override
  public NetSocket resume() {
    readStream.resume();
    return this;
  }

  @Override
  public synchronized NetSocket endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  synchronized Handler<Void> endHandler() {
    return endHandler;
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    writeStream.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public NetSocket drainHandler(Handler<Void> handler) {
    writeStream.drainHandler(handler);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return writeStream.writeQueueFull();
  }

  @Override
  public String writeHandlerID() {
    return null;
  }

  @Override
  public Future<Void> write(Buffer data) {
    return writeStream.write(data);
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    writeStream.write(data, handler);
  }

  @Override
  public Future<Void> write(String str, String enc) {
    return write(Buffer.buffer(str, enc));
  }

  @Override
  public void write(String str, String enc, Handler<AsyncResult<Void>> handler) {
    writeStream.write(Buffer.buffer(str, enc), handler);
  }

  @Override
  public Future<Void> write(String str) {
    return writeStream.write(Buffer.buffer(str));
  }

  @Override
  public void write(String str, Handler<AsyncResult<Void>> handler) {
    writeStream.write(Buffer.buffer(str), handler);
  }

  @Override
  public Future<Void> end(Buffer data) {
    return writeStream.end(data);
  }

  @Override
  public void end(Buffer buffer, Handler<AsyncResult<Void>> handler) {
    writeStream.end(buffer, handler);
  }

  @Override
  public Future<Void> end() {
    return writeStream.end();
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    writeStream.end(handler);
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    Promise<Void> promise = context.promise();
    sendFile(filename, offset, length, promise);
    return promise.future();
  }

  @Override
  public NetSocket sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
    VertxInternal vertx = conn.getContext().owner();
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
    HttpUtils.resolveFile(vertx, filename, offset, length, ar -> {
      if (ar.succeeded()) {
        AsyncFile file = ar.result();
        file.pipe()
          .endOnComplete(false)
          .to(this, ar1 -> file.close(ar2 -> {
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
    handler.handle(upgradeToSsl());
    return this;
  }

  @Override
  public NetSocket upgradeToSsl(String serverName, Handler<AsyncResult<Void>> handler) {
    handler.handle(upgradeToSsl(serverName));
    return this;
  }

  @Override
  public Future<Void> upgradeToSsl() {
    return Future.failedFuture("Cannot upgrade stream to SSL");
  }

  @Override
  public Future<Void> upgradeToSsl(String serverName) {
    return Future.failedFuture("Cannot upgrade stream to SSL");
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

  @Override
  public String applicationLayerProtocol() {
    return null;
  }
}
