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

import io.netty.buffer.ByteBuf;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.WebSocketBase;
import io.vertx.core.http.WebSocketFrame;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;

import java.util.UUID;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class WebSocketImplBase implements WebSocketBase {

  private final boolean supportsContinuation;
  private String textHandlerID;
  private String binaryHandlerID;
  private final int maxWebSocketFrameSize;
  private MessageConsumer<Buffer> binaryHandlerRegistration;
  private MessageConsumer<String> textHandlerRegistration;
  protected final ConnectionBase conn;

  protected Handler<WebSocketFrame> frameHandler;
  protected Handler<Buffer> dataHandler;
  protected Handler<Void> drainHandler;
  protected Handler<Throwable> exceptionHandler;
  protected Handler<Void> closeHandler;
  protected Handler<Void> endHandler;
  protected boolean closed;
  private EventBus eventBus;
  protected WebSocketImplBase(VertxInternal vertx, ConnectionBase conn, boolean supportsContinuation,
                              int maxWebSocketFrameSize) {
    this.supportsContinuation = supportsContinuation;
    this.conn = conn;
    this.eventBus = vertx.eventBus();
    this.maxWebSocketFrameSize = maxWebSocketFrameSize;
  }
  public String binaryHandlerID() {
    return binaryHandlerID(false);
  }
  
  public synchronized String binaryHandlerID(boolean cluster) {
    if(binaryHandlerID == null){
      this.binaryHandlerID = UUID.randomUUID().toString();
      Handler<Message<Buffer>> binaryHandler = msg -> writeBinaryFrameInternal(msg.body());
      binaryHandlerRegistration =(cluster?eventBus.<Buffer>consumer(binaryHandlerID):eventBus.<Buffer>localConsumer(binaryHandlerID)).handler(binaryHandler);
    }
    return binaryHandlerID;
  }
  
  public String textHandlerID() {
    return textHandlerID(false);
  }

  public synchronized String textHandlerID(boolean cluster) {
    if(textHandlerID==null){
      this.textHandlerID = UUID.randomUUID().toString();
      Handler<Message<String>> textHandler = msg -> writeTextFrameInternal(msg.body());
      textHandlerRegistration = (cluster?eventBus.<String>consumer(textHandlerID):eventBus.<String>localConsumer(textHandlerID)).handler(textHandler);
    }
    return textHandlerID;
  }

  public synchronized boolean writeQueueFull() {
    checkClosed();
    return conn.isNotWritable();
  }

  public synchronized void close() {
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

  public abstract WebSocketBase exceptionHandler(Handler<Throwable> handler);

  protected void writeMessageInternal(Buffer data) {
    checkClosed();
    writePartialMessage(data, 0);
  }

  protected void writePartialMessage(Buffer data, int offset) {
    int end = offset + maxWebSocketFrameSize;
    boolean isFinal;
    if (end >= data.length()) {
      end  = data.length();
      isFinal = true;
    } else {
      isFinal = false;
    }
    Buffer slice = data.slice(offset, end);
    WebSocketFrame frame;
    if (offset == 0 || !supportsContinuation) {
      frame = WebSocketFrame.binaryFrame(slice, isFinal);
    } else {
      frame = WebSocketFrame.continuationFrame(slice, isFinal);
    }
    writeFrame(frame);
    int newOffset = offset + maxWebSocketFrameSize;
    if (!isFinal) {
      writePartialMessage(data, newOffset);
    }
  }

  protected void writeBinaryFrameInternal(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    WebSocketFrame frame = new WebSocketFrameImpl(FrameType.BINARY, buf);
    writeFrame(frame);
  }

  protected void writeTextFrameInternal(String str) {
    WebSocketFrame frame = new WebSocketFrameImpl(str);
    writeFrame(frame);
  }

  protected synchronized void writeFrameInternal(WebSocketFrame frame) {
    checkClosed();
    conn.reportBytesWritten(frame.binaryData().length());
    conn.writeToChannel(frame);
  }

  protected void checkClosed() {
    if (closed) {
      throw new IllegalStateException("WebSocket is closed");
    }
  }

  synchronized void handleFrame(WebSocketFrameInternal frame) {
    conn.reportBytesRead(frame.binaryData().length());
    if (dataHandler != null) {
      Buffer buff = Buffer.buffer(frame.getBinaryData());
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

  synchronized void handleException(Throwable t) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    }
  }

  synchronized void handleClosed() {
    cleanupHandlers();
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  private void cleanupHandlers() {
    if (!closed) {
      if(binaryHandlerRegistration != null) binaryHandlerRegistration.unregister();
      if(textHandlerRegistration !=null) textHandlerRegistration.unregister();
      closed = true;
    }
  }

}
