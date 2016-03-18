/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Stream;
import io.netty.util.CharsetUtil;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.nio.charset.Charset;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class VertxHttp2NetSocket extends VertxHttp2Stream implements NetSocket {

  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Handler<Buffer> dataHandler;
  private Handler<Void> drainHandler;

  public VertxHttp2NetSocket(Vertx vertx, ContextImpl context, ChannelHandlerContext handlerContext, Http2ConnectionBase connection,
                             Http2ConnectionEncoder encoder, Http2ConnectionDecoder decoder, Http2Stream stream) {
    super(connection, stream);
  }

  // Stream impl

  @Override
  void callEnd() {
    try {
      if (endHandler != null) {
        // Give opportunity to send a last chunk
        endHandler.handle(null);
      }
    } finally {
      end();
    }
  }

  @Override
  void callHandler(Buffer buf) {
    if (dataHandler != null) {
      dataHandler.handle(buf);
    }
  }

  @Override
  void callReset(long errorCode) {
    handleException(new StreamResetException(errorCode));
  }

  @Override
  void handleException(Throwable cause) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(cause);
    }
  }

  @Override
  void handleClose() {
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  @Override
  void handleInterestedOpsChanged() {
    Handler<Void> handler = this.drainHandler;
    if (handler != null && !writeQueueFull()) {
      vertx.runOnContext(v -> {
        handler.handle(null);
      });
    }
  }

  // NetSocket impl

  @Override
  public NetSocket exceptionHandler(Handler<Throwable> handler) {
    exceptionHandler = handler;
    return this;
  }

  @Override
  public NetSocket handler(Handler<Buffer> handler) {
    dataHandler = handler;
    return this;
  }

  @Override
  public NetSocket pause() {
    return this;
  }

  @Override
  public NetSocket resume() {
    return this;
  }

  @Override
  public NetSocket endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }

  @Override
  public NetSocket write(Buffer data) {
    writeData(data.getByteBuf(), false);
    return this;
  }

  @Override
  public NetSocket setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public NetSocket drainHandler(Handler<Void> handler) {
    drainHandler = handler;
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return isNotWritable();
  }

  @Override
  public String writeHandlerID() {
    return null;
  }

  @Override
  public NetSocket write(String str) {
    writeData(Unpooled.copiedBuffer(str, CharsetUtil.UTF_8), false);
    return this;
  }

  @Override
  public NetSocket write(String str, String enc) {
    if (enc == null) {
      write(str);
    } else {
      writeData(Unpooled.copiedBuffer(str, Charset.forName(enc)), false);
    }
    return this;
  }

  @Override
  public NetSocket sendFile(String filename, long offset, long length) {
    return this;
  }

  @Override
  public NetSocket sendFile(String filename, long offset, long length, Handler<AsyncResult<Void>> resultHandler) {
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
  public void end() {
    writeData(Unpooled.EMPTY_BUFFER, true);
  }

  @Override
  public void end(Buffer buffer) {
    writeData(buffer.getByteBuf(), true);
  }

  @Override
  public void close() {
    end();
  }

  @Override
  public NetSocket closeHandler(@Nullable Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  @Override
  public NetSocket upgradeToSsl(Handler<Void> handler) {
    throw new UnsupportedOperationException("todo");
  }

  @Override
  public boolean isSsl() {
    throw new UnsupportedOperationException("todo");
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }
}
