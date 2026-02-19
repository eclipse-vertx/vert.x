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
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClosedException;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

import javax.net.ssl.SSLSession;
import java.time.Duration;
import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpNetSocket implements NetSocket {

  public static HttpNetSocket netSocket(HttpStream stream, ContextInternal context, ReadStream<Buffer> readStream, WriteStream<Buffer> writeStream) {
    HttpNetSocket sock = new HttpNetSocket(stream, context, readStream, writeStream);
    readStream.handler(sock::handleData);
    readStream.endHandler(sock::handleEnd);
    readStream.exceptionHandler(sock::handleException);
    stream.closeHandler(sock::handleClose);
    return sock;
  }

  private final HttpStream stream;
  private final ContextInternal context;
  private final ReadStream<Buffer> readStream;
  private final WriteStream<Buffer> writeStream;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Buffer> dataHandler;
  private boolean closed;

  private HttpNetSocket(HttpStream stream, ContextInternal context, ReadStream<Buffer> readStream, WriteStream<Buffer> writeStream) {
    this.stream = stream;
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
    if (!closed) {
      closed = true;
      Handler<Void> closeHandler = closeHandler();
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
    }
  }

  private void handleData(Buffer buf) {
    Handler<Buffer> handler = handler();
    if (handler != null) {
      handler.handle(buf);
    }
  }

  private void handleException(Throwable cause) {
    Handler<Throwable> handler = exceptionHandler();
    if (handler != null) {
      handler.handle(cause);
    }
    if (cause instanceof HttpClosedException) {
      Handler<Void> endHandler = endHandler();
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
  }

  private void handleClose(Void v) {
    if (!closed) {
      closed = true;
      Handler<Void> closeHandler = closeHandler();
      if (closeHandler != null) {
        closeHandler.handle(null);
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
  public Future<Void> write(String str, String enc) {
    return write(Buffer.buffer(str, enc));
  }

  @Override
  public Future<Void> write(String str) {
    return writeStream.write(Buffer.buffer(str));
  }

  @Override
  public Future<Void> end(Buffer data) {
    return writeStream.end(data);
  }

  @Override
  public Future<Void> end() {
    return writeStream.end();
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    return HttpUtils.resolveFile(stream.context(), filename, offset, length)
      .compose(file -> file
        .pipe()
        .endOnComplete(false)
        .to(this)
        .eventually(file::close)
      );
  }

  @Override
  public SocketAddress remoteAddress() {
    return stream.connection().remoteAddress();
  }

  @Override
  public SocketAddress remoteAddress(boolean real) {
    return stream.connection().remoteAddress(real);
  }

  @Override
  public SocketAddress localAddress() {
    return stream.connection().localAddress();
  }

  @Override
  public SocketAddress localAddress(boolean real) {
    return stream.connection().localAddress(real);
  }

  @Override
  public Iterable<Map.Entry<Buffer, Buffer>> tlvs() {
    return stream.connection().tlvs();
  }

  @Override
  public Future<Void> close() {
    return end();
  }

  @Override
  public NetSocket closeHandler(@Nullable Handler<Void> handler) {
    synchronized (stream) {
      closeHandler = handler;
    }
    return this;
  }

  @Override
  public NetSocket shutdownHandler(@Nullable Handler<Duration> handler) {
    // Not sure, we can do something here
    return this;
  }

  Handler<Void> closeHandler() {
    synchronized (stream) {
      return closeHandler;
    }
  }

  @Override
  public Future<Void> upgradeToSsl(SSLOptions sslOptions, String serverName, Buffer upgrade) {
    return context.failedFuture("Cannot upgrade stream to SSL");
  }

  @Override
  public boolean isSsl() {
    return stream.connection().isSsl();
  }

  @Override
  public SSLSession sslSession() {
    return stream.connection().sslSession();
  }

  @Override
  public String indicatedServerName() {
    return stream.connection().indicatedServerName();
  }

  @Override
  public String applicationLayerProtocol() {
    return null;
  }
}
