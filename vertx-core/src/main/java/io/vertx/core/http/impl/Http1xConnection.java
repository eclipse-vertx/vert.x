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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.channel.FileRegion;
import io.netty.handler.codec.http.FullHttpMessage;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.stream.ChunkedFile;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.GoAway;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpSettings;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.impl.VertxConnection;

import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class Http1xConnection extends VertxConnection implements io.vertx.core.http.HttpConnection {

  protected boolean closeInitiated;
  protected boolean shutdownInitiated;
  protected Object closeReason;
  protected long shutdownTimeout;
  protected TimeUnit shutdownUnit;
  protected ChannelPromise closePromise;

  Http1xConnection(ContextInternal context, ChannelHandlerContext chctx) {
    super(context, chctx);
  }

  @Override
  protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    shutdownInitiated = true;
    closeReason = reason;
    shutdownTimeout = timeout;
    shutdownUnit = unit;
    closePromise = promise;
  }

  @Override
  protected void handleClose(Object reason, ChannelPromise promise) {
    closeInitiated = true;
    super.handleClose(reason, promise);
  }

  protected void closeInternal() {
    if (closeInitiated) {
      // Nothing to do
    } else if (shutdownInitiated) {
      super.handleShutdown(closeReason, shutdownTimeout, shutdownUnit, closePromise);
    } else {
      chctx.channel().close();
    }
  }

  @Override
  public Http1xConnection closeHandler(Handler<Void> handler) {
    return (Http1xConnection) super.closeHandler(handler);
  }

  @Override
  public Http1xConnection shutdownHandler(Handler<Void> handler) {
    return (Http1xConnection) super.shutdownHandler(handler);
  }

  @Override
  public Http1xConnection exceptionHandler(Handler<Throwable> handler) {
    return (Http1xConnection) super.exceptionHandler(handler);
  }

  @Override
  public HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
  }

  @Override
  public HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support GOAWAY");
  }

  @Override
  public HttpSettings httpSettings() {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public Future<Void> updateHttpSettings(HttpSettings settings) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public HttpSettings remoteHttpSettings() {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }


  @Override
  public HttpConnection remoteHttpSettingsHandler(Handler<HttpSettings> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support SETTINGS");
  }

  @Override
  public HttpConnection pingHandler(@Nullable Handler<Buffer> handler) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support PING");
  }

  @Override
  public Future<Buffer> ping(Buffer data) {
    throw new UnsupportedOperationException("HTTP/1.x connections don't support PING");
  }

  @Override
  protected long sizeof(Object obj) {
    // https://github.com/netty/netty/issues/12708
    // try first Netty HTTP singleton types, without any instanceof/checkcast bytecodes
    if (obj == Unpooled.EMPTY_BUFFER || obj == LastHttpContent.EMPTY_LAST_CONTENT) {
      return 0;
    }
    // try known vertx (non-interface) types: bi-morphic
    if (obj instanceof AssembledHttpResponse) {
      return ((AssembledHttpResponse) obj).content().readableBytes();
    }
    if (obj instanceof Buffer) {
      return ((Buffer) obj).length();
    } else if (obj instanceof ByteBuf) {
      return ((ByteBuf) obj).readableBytes();
      // see Netty's HttpObjectEncoder::acceptOutboundMessage:
      // the order of checks is the same!
    } else if (obj instanceof FullHttpMessage) {
      return ((FullHttpMessage) obj).content().readableBytes();
    } else if (obj instanceof LastHttpContent) {
      return ((LastHttpContent) obj).content().readableBytes();
    } else if (obj instanceof  HttpContent) {
      return ((HttpContent) obj).content().readableBytes();
    } else if (obj instanceof FileRegion) {
      return ((FileRegion) obj).count();
    } else if (obj instanceof ChunkedFile) {
      ChunkedFile file = (ChunkedFile) obj;
      return file.endOffset() - file.startOffset();
    } else {
      return 0L;
    }
  }
}
