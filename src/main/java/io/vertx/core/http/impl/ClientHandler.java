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

import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.impl.ws.WebSocketFrameImpl;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;

import java.util.Map;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
class ClientHandler extends VertxHttpHandler<ClientConnection> {

  private boolean closeFrameSent;
  private ContextImpl context;

  public ClientHandler(Channel ch, ContextImpl context, Map<Channel, ClientConnection> connectionMap) {
    super(connectionMap, ch);
    this.context = context;
  }

  @Override
  protected ContextImpl getContext(ClientConnection connection) {
    return context;
  }

  @Override
  protected void doMessageReceived(ClientConnection conn, ChannelHandlerContext ctx, Object msg) {
    if (conn == null) {
      return;
    }
    boolean valid = false;
    if (msg instanceof HttpResponse) {
      HttpResponse response = (HttpResponse) msg;
      conn.handleResponse(response);
      valid = true;
    }
    if (msg instanceof HttpContent) {
      HttpContent chunk = (HttpContent) msg;
      if (chunk.content().isReadable()) {
        Buffer buff = Buffer.buffer(chunk.content().slice());
        conn.handleResponseChunk(buff);
      }
      if (chunk instanceof LastHttpContent) {
        conn.handleResponseEnd((LastHttpContent) chunk);
      }
      valid = true;
    } else if (msg instanceof WebSocketFrameInternal) {
      WebSocketFrameInternal frame = (WebSocketFrameInternal) msg;
      switch (frame.type()) {
        case BINARY:
        case CONTINUATION:
        case TEXT:
          conn.handleWsFrame(frame);
          break;
        case PING:
          // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
          ctx.writeAndFlush(new WebSocketFrameImpl(FrameType.PONG, frame.getBinaryData()));
          break;
        case PONG:
          // Just ignore it
          break;
        case CLOSE:
          if (!closeFrameSent) {
            // Echo back close frame and close the connection once it was written.
            // This is specified in the WebSockets RFC 6455 Section  5.4.1
            ctx.writeAndFlush(frame).addListener(ChannelFutureListener.CLOSE);
            closeFrameSent = true;
          }
          break;
        default:
          throw new IllegalStateException("Invalid type: " + frame.type());
      }
      valid = true;
    }
    if (!valid) {
      throw new IllegalStateException("Invalid object " + msg);
    }
  }
}
