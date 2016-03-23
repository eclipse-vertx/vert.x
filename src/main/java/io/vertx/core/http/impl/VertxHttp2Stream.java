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
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.Http2ConnectionDecoder;
import io.netty.handler.codec.http2.Http2ConnectionEncoder;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.handler.codec.http2.Http2Flags;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
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
  private final Http2ConnectionEncoder encoder;
  private final Http2ConnectionDecoder decoder;

  private final ArrayDeque<Object> pending = new ArrayDeque<>(8);
  private boolean paused;
  private boolean writable = true;

  VertxHttp2Stream(C conn, Http2Stream stream) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.handlerContext = conn.handlerContext;
    this.encoder = conn.handler.encoder();
    this.decoder = conn.handler.decoder();
    this.stream = stream;
    this.context = conn.getContext();
  }

  void onResetRead(long code) {
    paused = false;
    pending.clear();
    handleReset(code);
  }

  void onDataRead(Buffer data) {
    if (!paused) {
      if (pending.isEmpty()) {
        handleData(data);
        consume(data.length());
      } else {
        pending.add(data);
        checkNextTick(null);
      }
    } else {
      pending.add(data);
    }
  }

  void onWritabilityChanged() {
    writable = !writable;
    handleInterestedOpsChanged();
  }

  void onEnd() {
    if (paused || pending.size() > 0) {
      pending.add(END);
    } else {
      handleEnd();
    }
  }

  private void consume(int numBytes) {
    context.runOnContext(v -> {
      try {
        boolean windowUpdateSent = decoder.flowController().consumeBytes(stream, numBytes);
        if (windowUpdateSent) {
          handlerContext.channel().flush();
        }
      } catch (Http2Exception e) {
        e.printStackTrace();
      }
    });
  }

  private void checkNextTick(Void v) {
    if (!paused) {
      Object msg = pending.poll();
      if (msg instanceof Buffer) {
        Buffer buf = (Buffer) msg;
        consume(buf.length());
        handleData(buf);
        if (pending.size() > 0) {
          vertx.runOnContext(this::checkNextTick);
        }
      } if (msg == END) {
        handleEnd();
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
    return !writable;
  }

  void writeFrame(int type, int flags, ByteBuf payload) {
    encoder.writeFrame(handlerContext, (byte) type, stream.id(), new Http2Flags((short) flags), payload, handlerContext.newPromise());
    handlerContext.flush();
  }

  void writeHeaders(Http2Headers headers, boolean end) {
    encoder.writeHeaders(handlerContext, stream.id(), headers, 0, end, handlerContext.newPromise());;
  }

  void writeData(ByteBuf chunk, boolean end) {
    encoder.writeData(handlerContext, stream.id(), chunk, 0, end, handlerContext.newPromise());
    Http2RemoteFlowController controller = encoder.flowController();
    if (!controller.isWritable(stream) || end) {
      try {
        encoder.flowController().writePendingBytes();
      } catch (Http2Exception e) {
        e.printStackTrace();
      }
      handlerContext.channel().flush();
    }
  }

  void writeReset(long code) {
    encoder.writeRstStream(handlerContext, stream.id(), code, handlerContext.newPromise());
    handlerContext.flush();
  }

  void handleInterestedOpsChanged() {
  }

  void handleData(Buffer buf) {
  }

  void handleUnknownFrame(int type, int flags, Buffer buff) {
  }

  void handleEnd() {
  }

  void handleReset(long errorCode) {
  }

  void handleException(Throwable cause) {
  }

  void handleClose() {
  }
}
