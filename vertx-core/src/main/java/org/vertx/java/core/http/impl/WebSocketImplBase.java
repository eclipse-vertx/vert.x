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

package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Registration;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.WebSocketBase;
import org.vertx.java.core.http.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrameImpl;
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.SocketAddress;
import org.vertx.java.core.net.impl.ConnectionBase;

import java.util.UUID;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class WebSocketImplBase<T> implements WebSocketBase<T> {

  private final String textHandlerID;
  private final String binaryHandlerID;
  private final VertxInternal vertx;
  protected final ConnectionBase conn;

  protected Handler<WebSocketFrame> frameHandler;
  protected Handler<Buffer> dataHandler;
  protected Handler<Void> drainHandler;
  protected Handler<Throwable> exceptionHandler;
  protected Handler<Void> closeHandler;
  protected Handler<Void> endHandler;
  protected Registration binaryHandlerRegistration;
  protected Registration textHandlerRegistration;
  protected boolean closed;

  protected WebSocketImplBase(VertxInternal vertx, ConnectionBase conn) {
    this.vertx = vertx;
    this.textHandlerID = UUID.randomUUID().toString();
    this.binaryHandlerID = UUID.randomUUID().toString();
    this.conn = conn;
    Handler<Message<Buffer>> binaryHandler = msg -> writeBinaryFrameInternal(msg.body());
    binaryHandlerRegistration = vertx.eventBus().registerLocalHandler(binaryHandlerID, binaryHandler);
    Handler<Message<String>> textHandler = msg -> writeTextFrameInternal(msg.body());
    textHandlerRegistration = vertx.eventBus().registerLocalHandler(textHandlerID, textHandler);
  }

  public String binaryHandlerID() {
    return binaryHandlerID;
  }

  public String textHandlerID() {
    return textHandlerID;
  }

  public boolean writeQueueFull() {
    checkClosed();
    return conn.isNotWritable();
  }

  public void close() {
    checkClosed();
    conn.close();
    cleanupHandlers();
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  protected void writeBinaryFrameInternal(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    WebSocketFrame frame = new WebSocketFrameImpl(WebSocketFrame.FrameType.BINARY, buf);
    writeFrame(frame);
  }

  protected void writeTextFrameInternal(String str) {
    WebSocketFrame frame = new WebSocketFrameImpl(str);
    writeFrame(frame);
  }


  private void cleanupHandlers() {
    if (!closed) {
      binaryHandlerRegistration.unregister();
      textHandlerRegistration.unregister();
      closed = true;
    }
  }

  protected void writeFrame(WebSocketFrame frame) {
    checkClosed();
    conn.write(frame);
  }

  protected void checkClosed() {
    if (closed) {
      throw new IllegalStateException("WebSocket is closed");
    }
  }

  void handleFrame(WebSocketFrameInternal frame) {
    if (dataHandler != null) {
      Buffer buff = new Buffer(frame.getBinaryData());
      dataHandler.handle(buff);
    }

    if (frameHandler != null) {
      frameHandler.handle(frame);
    }
  }

  void writable() {
    if (drainHandler != null) {
      Handler<Void> dh = drainHandler;
      drainHandler = null;
      dh.handle(null);
    }
  }

  void handleException(Throwable t) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    }
  }

  void handleClosed() {
    cleanupHandlers();
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }
}
