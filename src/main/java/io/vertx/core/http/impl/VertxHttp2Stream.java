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

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;

import java.util.ArrayDeque;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttp2Stream<C extends Http2ConnectionBase> {

  private static final Object END = new Object(); // Marker

  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextImpl context;
  protected final ChannelHandlerContext handlerContext;
  protected final Http2Stream stream;

  private final ArrayDeque<Object> pending = new ArrayDeque<>(8);
  private boolean paused;
  private boolean writable = true;

  VertxHttp2Stream(C conn, Http2Stream stream) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.handlerContext = conn.handlerContext;
    this.stream = stream;
    this.context = conn.getContext();
  }

  void onResetRead(long code) {
    synchronized (conn) {
      paused = false;
      pending.clear();
      handleReset(code);
    }
  }

  boolean onDataRead(Buffer data) {
    synchronized (conn) {
      if (!paused) {
        if (pending.isEmpty()) {
          handleData(data);
          return true;
        } else {
          pending.add(data);
          checkNextTick(null);
        }
      } else {
        pending.add(data);
      }
      return false;
    }
  }

  void onWritabilityChanged() {
    synchronized (conn) {
      writable = !writable;
      handleInterestedOpsChanged();
    }
  }

  void onEnd() {
    onEnd(null);
  }

  void onEnd(MultiMap trailers) {
    synchronized (conn) {
      if (paused || pending.size() > 0) {
        if (trailers != null) {
          pending.add(trailers);
        } else {
          pending.add(END);
        }
      } else {
        handleEnd(trailers);
      }
    }
  }

  /**
   * Check if paused buffers must be handled to the reader, this must be called from event loop.
   */
  private void checkNextTick(Void v) {
    synchronized (conn) {
      if (!paused) {
        Object msg = pending.poll();
        if (msg instanceof Buffer) {
          Buffer buf = (Buffer) msg;
          conn.handler.consume(stream, buf.length());
          handleData(buf);
          if (pending.size() > 0) {
            vertx.runOnContext(this::checkNextTick);
          }
        } else if (msg == END) {
          handleEnd(null);
        } else if (msg instanceof MultiMap) {
          handleEnd((MultiMap) msg);
        }
      }
    }
  }

  int id() {
    return stream.id();
  }

  public void doPause() {
    paused = true;
  }

  public void doResume() {
    paused = false;
    context.runOnContext(this::checkNextTick);
  }

  boolean isNotWritable() {
    synchronized (conn) {
      return !writable;
    }
  }

  void writeFrame(int type, int flags, ByteBuf payload) {
    conn.handler.writeFrame(stream, (byte) type, (short) flags, payload);
  }

  void writeHeaders(Http2Headers headers, boolean end) {
    conn.handler.writeHeaders(stream, headers, end);
  }

  void writeData(ByteBuf chunk, boolean end) {
    conn.handler.writeData(stream, chunk, end);
  }

  void writeReset(long code) {
    conn.handler.writeReset(stream.id(), code);
  }

  void handleInterestedOpsChanged() {
  }

  void handleData(Buffer buf) {
  }

  void handleUnknownFrame(int type, int flags, Buffer buff) {
  }

  void handleEnd(MultiMap trailers) {
  }

  void handleReset(long errorCode) {
  }

  void handleException(Throwable cause) {
  }

  void handleClose() {
  }
}
