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
import io.netty.handler.codec.http2.Http2RemoteFlowController;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.Vertx;
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
  protected final Channel channel;
  protected final ChannelHandlerContext handlerContext;
  protected final Http2ConnectionEncoder encoder;
  protected final Http2ConnectionDecoder decoder;
  protected final Http2Stream stream;

  private final ArrayDeque<Object> pending = new ArrayDeque<>(8);
  private boolean paused;

  VertxHttp2Stream(C conn, Http2Stream stream) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.handlerContext = conn.handlerContext;
    this.encoder = conn.handler.encoder();
    this.decoder = conn.handler.decoder();
    this.stream = stream;
    this.context = conn.getContext();
    this.channel = conn.channel;
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

  private void consume(int numBytes) {
    context.runOnContext(v -> {
      try {
        boolean windowUpdateSent = decoder.flowController().consumeBytes(stream, numBytes);
        if (windowUpdateSent) {
          channel.flush();
        }
      } catch (Http2Exception e) {
        e.printStackTrace();
      }
    });
  }

  void handleEnd() {
    if (paused || pending.size() > 0) {
      pending.add(END);
    } else {
      callEnd();
    }
  }

  void handleData(Buffer data) {
    if (!paused) {
      if (pending.isEmpty()) {
        callHandler(data);
        consume(data.length());
      } else {
        pending.add(data);
        checkNextTick(null);
      }
    } else {
      pending.add(data);
    }
  }

  private void checkNextTick(Void v) {
    if (!paused) {
      Object msg = pending.poll();
      if (msg instanceof Buffer) {
        Buffer buf = (Buffer) msg;
        consume(buf.length());
        callHandler(buf);
        if (pending.size() > 0) {
          vertx.runOnContext(this::checkNextTick);
        }
      } if (msg == END) {
        callEnd();
      }
    }
  }

  void handleReset(long code) {
    paused = false;
    pending.clear();
    callReset(code);
  }

  abstract void callEnd();

  abstract void callHandler(Buffer buf);

  abstract void callReset(long errorCode);

  abstract void handleException(Throwable cause);

  abstract void handleClose();

  abstract void handleInterestedOpsChanged();

  void handleUnknownFrame(int type, int flags, Buffer buff) {
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
      channel.flush();
    }
  }

  boolean isNotWritable() {
    return !encoder.flowController().isWritable(stream);
  }
}
