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

import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;

import java.io.UnsupportedEncodingException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.WebSocketBase;
import org.vertx.java.core.http.WebSocketFrame;
import org.vertx.java.core.http.impl.ws.DefaultWebSocketFrame;
import org.vertx.java.core.http.impl.ws.WebSocketFrameInternal;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.net.impl.ConnectionBase;

/**
 * 
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class WebSocketImplBase<T> implements WebSocketBase<T> {

  /**
   * size of the websocket chunk. This is the default value, maybe should be
   * made configurable
   */
  private static final int DEFAULT_CHUNK_SIZE = 65536;

  private int chunkSize = DEFAULT_CHUNK_SIZE;

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
  protected Handler<Message<Buffer>> binaryHandler;
  protected Handler<Message<String>> textHandler;
  protected boolean closed;

  private CompositeByteBuf wsFramesCollector;

  protected WebSocketImplBase(VertxInternal vertx, ConnectionBase conn) {
    this.vertx = vertx;
    this.textHandlerID = UUID.randomUUID().toString();
    this.binaryHandlerID = UUID.randomUUID().toString();
    this.conn = conn;
    wsFramesCollector = Unpooled.compositeBuffer();
    binaryHandler = new Handler<Message<Buffer>>() {
      public void handle(Message<Buffer> msg) {
        writeBinaryFrameInternal(msg.body());
      }
    };
    vertx.eventBus().registerLocalHandler(binaryHandlerID, binaryHandler);
    textHandler = new Handler<Message<String>>() {
      public void handle(Message<String> msg) {
        writeTextFrameInternal(msg.body());
      }
    };
    vertx.eventBus().registerLocalHandler(textHandlerID, textHandler);
  }

  public String binaryHandlerID() {
    return binaryHandlerID;
  }

  public String textHandlerID() {
    return textHandlerID;
  }

  public boolean writeQueueFull() {
    checkClosed();
    return conn.doWriteQueueFull();
  }

  public void close() {
    checkClosed();
    conn.close();
    cleanupHandlers();
  }

  @Override
  public InetSocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public InetSocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  protected void writeBinaryFrameInternal(Buffer data) {
    if (data != null && data.getBytes() != null && data.getBytes().length != 0) {
      byte[][] chunks = chunkMessage(data.getBytes());
      for (int i = 0; i < chunks.length; i++) {
        boolean finalFrame = i == chunks.length - 1;
        WebSocketFrame.FrameType frameType = i == 0 ? WebSocketFrame.FrameType.BINARY : WebSocketFrame.FrameType.CONTINUATION;
        WebSocketFrame frame = new DefaultWebSocketFrame(frameType, Unpooled.copiedBuffer(chunks[i]), finalFrame);
        writeFrame(frame);
      }
    } else {
      WebSocketFrame frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.BINARY);
      writeFrame(frame);
    }
  }

  protected void writeTextFrameInternal(String data) {
    if (data != null && !data.isEmpty()) {
      List<String> chunks = chunkMessage(data);
      for (int i = 0; i < chunks.size(); i++) {
        boolean finalFrame = i == chunks.size() - 1;
        WebSocketFrame frame;
        if (i == 0) {
          frame = new DefaultWebSocketFrame(chunks.get(i), finalFrame);
        } else {
          try {
            frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.CONTINUATION, Unpooled.copiedBuffer(chunks.get(i).getBytes("UTF-8")),
                finalFrame);
          } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
          }
        }
        writeFrame(frame);
      }
    } else {
      WebSocketFrame frame = new DefaultWebSocketFrame(WebSocketFrame.FrameType.TEXT);
      writeFrame(frame);
    }
  }

  /**
   * @param message
   *          the message to be chunked. Is assumed to be a not-null array
   * @return bytes array with the message chunks
   */
  byte[][] chunkMessage(byte[] messageBytes) {
    byte[][] resChunks = new byte[messageBytes.length / chunkSize + (messageBytes.length % chunkSize > 0 ? 1 : 0)][];
    for (int chunkIndex = 0; chunkIndex < resChunks.length; chunkIndex++) {
      resChunks[chunkIndex] = Arrays.copyOfRange(messageBytes, chunkIndex * chunkSize, Math.min((chunkIndex + 1) * chunkSize, messageBytes.length));
    }
    return resChunks;
  }

  /**
   * splits the provided string into chunks of byte lengths not exceding the
   * CHUNK_SIZE
   * 
   * @param str
   *          the string to be splitted. Is assumed to be a not-null string
   * 
   * @return list of chunks
   */
  List<String> chunkMessage(String str) {
    int offset = 0;
    List<String> chunks = new ArrayList<>();
    while (offset < str.length()) {
      String chunk = getUTF8LongestPrefix(str.substring(offset), chunkSize);
      chunks.add(chunk);
      offset += chunk.length();
    }
    return chunks;
  }

  /**
   * 
   * @param str
   *          the string. It is assumed to be a not-null string
   * @param bytesLengthLimit
   *          size limit of the prefix. It is assumed to be non-negative integer
   * @return the longest prefix that does not exceed the bytesLengthLimit when
   *         encoded with UTF-8 scheme
   */
  String getUTF8LongestPrefix(String str, int bytesLengthLimit) {
    int prefixLengthInBytes = 0;
    int currentIndex = 0;
    while (prefixLengthInBytes <= bytesLengthLimit && currentIndex < str.length()) {
      int cp = str.codePointAt(currentIndex);
      prefixLengthInBytes += getBytesAmountForUTF8Encoding(cp);
      if (!Character.isSupplementaryCodePoint(cp))
        currentIndex++;
      else
        currentIndex += 2;
    }
    return str.substring(0, currentIndex - (prefixLengthInBytes > bytesLengthLimit ? 1 : 0));
  }

  /**
   * @param codePoint
   *          the codePoint
   * @return amount of bytes that are needed to encode the specified codePoint
   *         with UTF-8 scheme (rfc 3629)
   */
  private int getBytesAmountForUTF8Encoding(int codePoint) {
    /*
     * input data is not checked since it is a private method, and the code
     * point is guaranteed to be of the valid value
     */
    if (codePoint < 0x0080)
      return 1;
    else if (codePoint < 0x0800)
      return 2;
    else if (codePoint < 0x00010000)
      return 3;
    else if (codePoint < 0x00110000)
      return 4;
    throw new IllegalArgumentException("The code point must be in the range 0x0000 to 0x0010FFFF");
  }

  private void cleanupHandlers() {
    if (!closed) {
      vertx.eventBus().unregisterHandler(binaryHandlerID, binaryHandler);
      vertx.eventBus().unregisterHandler(textHandlerID, textHandler);
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
      switch (frame.type()) {
      case PING:
      case PONG:
      case CLOSE:
      case TEXT:
      case BINARY:
        wsFramesCollector.clear();
        wsFramesCollector.removeComponents(0, wsFramesCollector.numComponents());
      case CONTINUATION:
        wsFramesCollector.addComponent(frame.getBinaryData());
      }
      if (frame.isFinalFrame()) {
        wsFramesCollector.writerIndex(wsFramesCollector.capacity());
        Buffer buff = new Buffer(wsFramesCollector);
        dataHandler.handle(buff);
      }
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

  public int getChunkSize() {
    return chunkSize;
  }

  public void setChunkSize(int chunkSize) {
    this.chunkSize = chunkSize;
  }
}
