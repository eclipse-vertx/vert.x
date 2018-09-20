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
import io.vertx.core.queue.Queue;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.cert.X509Certificate;
import java.util.UUID;

/**
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 * <p>
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 * @param <S> self return type
 */
public abstract class WebSocketImplBase<S extends WebSocketBase> implements WebSocketBase {

  private final boolean supportsContinuation;
  private final String textHandlerID;
  private final String binaryHandlerID;
  private final int maxWebSocketFrameSize;
  private final int maxWebSocketMessageSize;
  private final Queue<Buffer> pending;
  private MessageConsumer binaryHandlerRegistration;
  private MessageConsumer textHandlerRegistration;
  private String subProtocol;
  private Object metric;
  private Handler<WebSocketFrameInternal> frameHandler;
  private Handler<Buffer> pongHandler;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  protected final Http1xConnectionBase conn;
  protected boolean closed;


  WebSocketImplBase(VertxInternal vertx, Http1xConnectionBase conn, boolean supportsContinuation,
                              int maxWebSocketFrameSize, int maxWebSocketMessageSize) {
    this.supportsContinuation = supportsContinuation;
    this.textHandlerID = "__vertx.ws." + UUID.randomUUID().toString();
    this.binaryHandlerID = "__vertx.ws." + UUID.randomUUID().toString();
    this.conn = conn;
    this.maxWebSocketFrameSize = maxWebSocketFrameSize;
    this.maxWebSocketMessageSize = maxWebSocketMessageSize;
    this.pending = Queue.<Buffer>queue(conn.getContext());

    pending.writableHandler(v -> {
      conn.doResume();
    });
  }

  void registerHandler(EventBus eventBus) {
    Handler<Message<Buffer>> binaryHandler = msg -> writeBinaryFrameInternal(msg.body());
    Handler<Message<String>> textHandler = msg -> writeTextFrameInternal(msg.body());
    binaryHandlerRegistration = eventBus.<Buffer>localConsumer(binaryHandlerID).handler(binaryHandler);
    textHandlerRegistration = eventBus.<String>localConsumer(textHandlerID).handler(textHandler);
  }

  public String binaryHandlerID() {
    return binaryHandlerID;
  }

  public String textHandlerID() {
    return textHandlerID;
  }

  public boolean writeQueueFull() {
    synchronized (conn) {
      checkClosed();
      return conn.isNotWritable();
    }
  }

  public void close() {
    synchronized (conn) {
      checkClosed();
      conn.close();
      cleanupHandlers();
    }
  }

  public void close(short statusCode) {
    this.close(statusCode, null);
  }

  public void close(short statusCode, String reason) {
    synchronized (conn) {
      checkClosed();
      conn.closeWithPayload(HttpUtils.generateWSCloseFrameByteBuf(statusCode, reason));
      cleanupHandlers();
    }
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
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public S writeFinalTextFrame(String text) {
    return (S) writeFrame(WebSocketFrame.textFrame(text, true));
  }

  @Override
  public S writeFinalBinaryFrame(Buffer data) {
    return (S) writeFrame(WebSocketFrame.binaryFrame(data, true));
  }

  @Override
  public String subProtocol() {
    synchronized(conn) {
      return subProtocol;
    }
  }

  void subProtocol(String subProtocol) {
    synchronized (conn) {
      this.subProtocol = subProtocol;
    }
  }

  @Override
  public S writeBinaryMessage(Buffer data) {
    synchronized (conn) {
      checkClosed();
      writeMessageInternal(data);
      return (S) this;
    }
  }

  @Override
  public S writeTextMessage(String text) {
    synchronized (conn) {
      checkClosed();
      writeTextMessageInternal(text);
      return (S) this;
    }
  }

  @Override
  public S write(Buffer data) {
    synchronized (conn) {
      checkClosed();
      writeFrame(WebSocketFrame.binaryFrame(data, true));
      return (S) this;
    }
  }

  @Override
  public S writePing(Buffer data) {
    if(data.length() > maxWebSocketFrameSize || data.length() > 125) throw new IllegalStateException("Ping cannot exceed maxWebSocketFrameSize or 125 bytes");
    return writeFrame(WebSocketFrame.pingFrame(data));
  }

  @Override
  public S writePong(Buffer data) {
    if(data.length() > maxWebSocketFrameSize || data.length() > 125) throw new IllegalStateException("Pong cannot exceed maxWebSocketFrameSize or 125 bytes");
    return writeFrame(WebSocketFrame.pongFrame(data));
  }

  private void writeMessageInternal(Buffer data) {
    checkClosed();
    writePartialMessage(FrameType.BINARY, data, 0);
  }

  private void writeTextMessageInternal(String text) {
    checkClosed();
    Buffer data = Buffer.buffer(text);
    writePartialMessage(FrameType.TEXT, data, 0);
  }

  /**
   * Splits the provided buffer into multiple frames (which do not exceed the maximum web socket frame size)
   * and writes them in order to the socket.
   */
  private void writePartialMessage(FrameType frameType, Buffer data, int offset) {
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
      frame = new WebSocketFrameImpl(frameType, slice.getByteBuf(), isFinal);
    } else {
      frame = WebSocketFrame.continuationFrame(slice, isFinal);
    }
    writeFrame(frame);
    int newOffset = offset + maxWebSocketFrameSize;
    if (!isFinal) {
      writePartialMessage(frameType, data, newOffset);
    }
  }

  private void writeBinaryFrameInternal(Buffer data) {
    ByteBuf buf = data.getByteBuf();
    WebSocketFrame frame = new WebSocketFrameImpl(FrameType.BINARY, buf);
    writeFrame(frame);
  }

  private void writeTextFrameInternal(String str) {
    WebSocketFrame frame = new WebSocketFrameImpl(str);
    writeFrame(frame);
  }

  @Override
  public S writeFrame(WebSocketFrame frame) {
    synchronized (conn) {
      checkClosed();
      conn.reportBytesWritten(((WebSocketFrameInternal)frame).length());
      conn.writeToChannel(conn.encodeFrame((WebSocketFrameImpl) frame));
    }
    return (S) this;
  }

  void checkClosed() {
    synchronized (conn) {
      if (closed) {
        throw new IllegalStateException("WebSocket is closed");
      }
    }
  }

  boolean isClosed() {
    synchronized (conn) {
      return closed;
    }
  }

  void handleFrame(WebSocketFrameInternal frame) {
    synchronized (conn) {
      if (frame.type() != FrameType.CLOSE) {
        conn.reportBytesRead(frame.length());
        if (!pending.add(frame.binaryData())) {
          conn.doPause();
        }
      }
      switch(frame.type()) {
        case PONG:
          if (pongHandler != null) {
            pongHandler.handle(frame.binaryData());
          }
          break;
        case TEXT:
        case CLOSE:
        case BINARY:
        case CONTINUATION:
          if (frameHandler != null) {
            frameHandler.handle(frame);
          }
          break;
      }
    }
  }

  private class FrameAggregator implements Handler<WebSocketFrameInternal> {
    private Handler<String> textMessageHandler;
    private Handler<Buffer> binaryMessageHandler;

    private Buffer textMessageBuffer;
    private Buffer binaryMessageBuffer;

    @Override
    public void handle(WebSocketFrameInternal frame) {
      switch (frame.type()) {
        case TEXT:
          handleTextFrame(frame);
          break;
        case BINARY:
          handleBinaryFrame(frame);
          break;
        case CONTINUATION:
          if (textMessageBuffer != null && textMessageBuffer.length() > 0) {
            handleTextFrame(frame);
          } else if (binaryMessageBuffer != null && binaryMessageBuffer.length() > 0) {
            handleBinaryFrame(frame);
          }
          break;
      }
    }

    private void handleTextFrame(WebSocketFrameInternal frame) {
      Buffer frameBuffer = Buffer.buffer(frame.getBinaryData());
      if (textMessageBuffer == null) {
        textMessageBuffer = frameBuffer;
      } else {
        textMessageBuffer.appendBuffer(frameBuffer);
      }
      if (textMessageBuffer.length() > maxWebSocketMessageSize) {
        int len = textMessageBuffer.length() - frameBuffer.length();
        textMessageBuffer = null;
        String msg = "Cannot process text frame of size " + frameBuffer.length() + ", it would cause message buffer (size " +
            len + ") to overflow max message size of " + maxWebSocketMessageSize;
        handleException(new IllegalStateException(msg));
        return;
      }
      if (frame.isFinal()) {
        String fullMessage = textMessageBuffer.toString();
        textMessageBuffer = null;
        if (textMessageHandler != null) {
          textMessageHandler.handle(fullMessage);
        }
      }
    }

    private void handleBinaryFrame(WebSocketFrameInternal frame) {
      Buffer frameBuffer = Buffer.buffer(frame.getBinaryData());
      if (binaryMessageBuffer == null) {
        binaryMessageBuffer = frameBuffer;
      } else {
        binaryMessageBuffer.appendBuffer(frameBuffer);
      }
      if (binaryMessageBuffer.length() > maxWebSocketMessageSize) {
        int len = binaryMessageBuffer.length() - frameBuffer.length();
        binaryMessageBuffer = null;
        String msg = "Cannot process binary frame of size " + frameBuffer.length() + ", it would cause message buffer (size " +
            len + ") to overflow max message size of " + maxWebSocketMessageSize;
        handleException(new IllegalStateException(msg));
        return;
      }
      if (frame.isFinal()) {
        Buffer fullMessage = binaryMessageBuffer.copy();
        binaryMessageBuffer = null;
        if (binaryMessageHandler != null) {
          binaryMessageHandler.handle(fullMessage);
        }
      }
    }
  }

  @Override
  public S frameHandler(Handler<WebSocketFrame> handler) {
    synchronized (conn) {
      checkClosed();
      this.frameHandler = (Handler)handler;
      return (S) this;
    }
  }

  @Override
  public WebSocketBase textMessageHandler(Handler<String> handler) {
    synchronized (conn) {
      checkClosed();
      if (frameHandler == null || frameHandler.getClass() != FrameAggregator.class) {
        frameHandler = new FrameAggregator();
      }
      ((FrameAggregator) frameHandler).textMessageHandler = handler;
      return this;
    }
  }

  @Override
  public S binaryMessageHandler(Handler<Buffer> handler) {
    synchronized (conn) {
      checkClosed();
      if (frameHandler == null || frameHandler.getClass() != FrameAggregator.class) {
        frameHandler = new FrameAggregator();
      }
      ((FrameAggregator) frameHandler).binaryMessageHandler = handler;
      return (S) this;
    }
  }

  @Override
  public WebSocketBase pongHandler(Handler<Buffer> handler) {
    synchronized (conn) {
      checkClosed();
      this.pongHandler = handler;
      return (S) this;
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
    synchronized (conn) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(t);
      }
    }
  }

  void handleClosed() {
    Handler<Void> endHandler;
    synchronized (conn) {
      cleanupHandlers();
      endHandler = this.endHandler;
      Handler<Void> closeHandler = this.closeHandler;
      if (closeHandler != null) {
        conn.getContext().runOnContext(closeHandler);
      }
    }
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  private void cleanupHandlers() {
    if (!closed) {
      closed = true;
      if (binaryHandlerRegistration != null) {
        binaryHandlerRegistration.unregister();
        binaryHandlerRegistration = null;
      }
      if (textHandlerRegistration != null) {
        textHandlerRegistration.unregister();
        textHandlerRegistration = null;
      }
    }
  }

  synchronized void setMetric(Object metric) {
    this.metric = metric;
  }

  synchronized Object getMetric() {
    return metric;
  }

  @Override
  public S handler(Handler<Buffer> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      pending.handler(handler);
      return (S) this;
    }
  }

  @Override
  public S endHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.endHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkClosed();
      }
      this.exceptionHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.closeHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      checkClosed();
      this.drainHandler = handler;
      return (S) this;
    }
  }

  @Override
  public S pause() {
    if (!isClosed()) {
      pending.pause();
    }
    return (S) this;
  }

  @Override
  public S resume() {
    if (!isClosed()) {
      pending.resume();
    }
    return (S) this;
  }

  @Override
  public S fetch(long amount) {
    if (!isClosed()) {
      pending.take(amount);
    }
    return (S) this;
  }

  @Override
  public S setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkClosed();
      conn.doSetWriteQueueMaxSize(maxSize);
      return (S) this;
    }
  }

  @Override
  public void end() {
    close();
  }
}
