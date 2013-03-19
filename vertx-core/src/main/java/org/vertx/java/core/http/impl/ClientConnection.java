/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.buffer.BufUtil;
import io.netty.buffer.MessageBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.WebSocket;
import org.vertx.java.core.http.WebSocketVersion;
import org.vertx.java.core.http.impl.ws.WebSocketConvertHandler;
import org.vertx.java.core.http.impl.ws.WebSocketFrame;
import org.vertx.java.core.impl.Context;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.URI;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class ClientConnection extends AbstractConnection {

  private static final Logger log = LoggerFactory.getLogger(ClientConnection.class);

  ClientConnection(VertxInternal vertx, DefaultHttpClient client, Channel channel, String hostHeader,
                   boolean keepAlive,
                   Context context) {
    super(vertx, channel, context);
    this.client = client;
    this.hostHeader = hostHeader;
    this.keepAlive = keepAlive;
  }

  final DefaultHttpClient client;
  final String hostHeader;
  boolean keepAlive;
  private boolean wsHandshakeConnection;
  private WebSocketClientHandshaker handshaker;
  private volatile DefaultHttpClientRequest currentRequest;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Queue<DefaultHttpClientRequest> requests = new ConcurrentLinkedQueue();
  private volatile DefaultHttpClientResponse currentResponse;
  private DefaultWebSocket ws;

  void toWebSocket(String uri,
                   final Handler<WebSocket> wsConnect,
                   final WebSocketVersion wsVersion) {
    if (ws != null) {
      throw new IllegalStateException("Already websocket");
    }

    try {
      if (uri.startsWith("/")){
          uri = "http://localhost" + uri;
      }
      URI wsuri = new URI(uri);
      io.netty.handler.codec.http.websocketx.WebSocketVersion version;
      if (wsVersion == WebSocketVersion.HYBI_00) {
        version = io.netty.handler.codec.http.websocketx.WebSocketVersion.V00;
      } else if (wsVersion == WebSocketVersion.HYBI_08) {
        version = io.netty.handler.codec.http.websocketx.WebSocketVersion.V08;
      } else if (wsVersion == WebSocketVersion.RFC6455) {
        version = io.netty.handler.codec.http.websocketx.WebSocketVersion.V13;
      } else {
        throw new IllegalArgumentException("Invalid version");
      }
      handshaker = WebSocketClientHandshakerFactory.newHandshaker(wsuri, version, null, false, HttpHeaders.EMPTY_HEADERS);
      final ChannelPipeline p = channel.pipeline();
      p.addBefore("handler", "handshakeCompleter", new HandshakeInboundHandler(wsConnect));

      handshaker.handshake(channel).addListener(new ChannelFutureListener() {
        @Override
        public void operationComplete(ChannelFuture future) throws Exception {
          if (!future.isSuccess()) {
            client.handleException((Exception) future.cause());
          }
        }
      });
      wsHandshakeConnection = true;

    } catch (Exception e) {
      handleException(e);
    }
  }

  private final class HandshakeInboundHandler extends ChannelStateHandlerAdapter implements ChannelInboundMessageHandler<Object> {
    private final Handler<WebSocket> wsConnect;
    private final Context context;
    private FullHttpResponse response;
    private boolean handshaking;

    public HandshakeInboundHandler(final Handler<WebSocket> wsConnect) {
      this.wsConnect = wsConnect;
      this.context = vertx.getContext();
    }

    @Override
    public void inboundBufferUpdated(ChannelHandlerContext ctx) throws Exception {
      MessageBuf<Object> in = ctx.inboundMessageBuffer();
      for (; ; ) {
        boolean handled = false;

        Object msg = in.poll();
        if (msg == null) {
          break;
        }
        if (handshaker != null && !handshaking) {
          if (msg instanceof HttpResponse) {
            handled = true;
            HttpResponse resp = (HttpResponse) msg;
            if (resp.getStatus().code() != 101) {
              client.handleException(new WebSocketHandshakeException("Websocket connection attempt returned HTTP status code " + resp.getStatus().code()));
              return;
            }
            if (msg instanceof FullHttpResponse) {
              handshakeComplete(ctx, (FullHttpResponse) msg);
              return;
            } else {
              response = new DefaultFullHttpResponse(resp.getProtocolVersion(), resp.getStatus());
              response.headers().add(resp.headers());
            }
          }
          if (msg instanceof HttpContent) {
            if (response != null) {
              handled = true;

              response.data().writeBytes(((HttpContent) msg).data());
              if (msg instanceof LastHttpContent) {
                response.trailingHeaders().add(((LastHttpContent) msg).trailingHeaders());
                handshakeComplete(ctx, response);
                return;
              }
            }
          }
        }
        if (!handled) {
          BufUtil.retain(msg);
          ctx.nextInboundMessageBuffer().add(msg);
        }
      }
    }

    @Override
    public MessageBuf<Object> newInboundBuffer(ChannelHandlerContext ctx) throws Exception {
      return Unpooled.messageBuffer();
    }

    @Override
    public void freeInboundBuffer(ChannelHandlerContext ctx) throws Exception {
      ctx.inboundMessageBuffer().release();
    }

    private void handshakeComplete(ChannelHandlerContext ctx, FullHttpResponse response) {
      handshaking = true;
      try {
        ctx.pipeline().addAfter(ctx.name(), "websocketConverter", WebSocketConvertHandler.INSTANCE);
        handshaker.finishHandshake(channel, response);
        ws = new DefaultWebSocket(vertx, ClientConnection.this);
        if (context.isOnCorrectWorker(ctx.channel().eventLoop())) {
          vertx.setContext(context);
          wsConnect.handle(ws);
        } else {
          context.execute(new Runnable() {
            public void run() {
              wsConnect.handle(ws);
            }
          });
        }
      } catch (WebSocketHandshakeException e) {
        client.handleException(e);
      } finally {
        ctx.pipeline().removeAndForward(this);
        ctx.fireInboundBufferUpdated();
      }
    }
  }

  @Override
  public void close() {
//    if (ws != null) {
//      //Need to send 9 zeros to represent a close
//      byte[] bytes = new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0};  // Just to be explicit
//      ChannelFuture future = channel.write(ChannelBuffers.copiedBuffer(bytes));
//      future.addListener(ChannelFutureListener.CLOSE);  // Close after it's written
//    }
    if (wsHandshakeConnection) {
      // Do nothing - this will be ugraded
    } else if (!keepAlive) {
      //Close it
      internalClose();
    } else {
      client.returnConnection(this);
    }
  }

  void internalClose() {
    channel.close();
  }

  boolean isClosed() {
    return !channel.isOpen();
  }

  int getOutstandingRequestCount() {
    return requests.size();
  }

  //TODO - combine these with same in ServerConnection and NetSocket
  @Override
  public void handleInterestedOpsChanged() {
    try {
      if (!doWriteQueueFull()) {
        if (currentRequest != null) {
          setContext();
          currentRequest.handleDrained();
        } else if (ws != null) {
          ws.writable();
        }
      }
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }


  void handleResponse(HttpResponse resp) {
    DefaultHttpClientRequest req;
    if (resp.getStatus().code() == 100) {
      //If we get a 100 continue it will be followed by the real response later, so we don't remove it yet
      req = requests.peek();
    } else {
      req = requests.poll();
    }
    if (req == null) {
      throw new IllegalStateException("No response handler");
    }
    setContext();
    DefaultHttpClientResponse nResp = new DefaultHttpClientResponse(req, this, resp);
    currentResponse = nResp;
    req.handleResponse(nResp);
  }

  void handleResponseChunk(Buffer buff) {
    setContext();
    try {
      currentResponse.handleChunk(buff);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
  }

  void handleResponseEnd(LastHttpContent trailer) {
    setContext();
    try {
      currentResponse.handleEnd(trailer);
    } catch (Throwable t) {
      handleHandlerException(t);
    }
    if (!keepAlive) {
      close();
    }
  }

  void handleWsFrame(WebSocketFrame frame) {
    if (ws != null) {
      setContext();
      ws.handleFrame(frame);
    }
  }

  protected void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
  }

  protected Context getContext() {
    return super.getContext();
  }

  protected void handleException(Exception e) {
    super.handleException(e);

    if (currentRequest != null) {
      currentRequest.handleException(e);
    } else if (currentResponse != null) {
      currentResponse.handleException(e);
    }
  }

  protected void addFuture(AsyncResultHandler<Void> doneHandler, ChannelFuture future) {
    super.addFuture(doneHandler, future);
  }

  ChannelFuture write(Object obj) {
    if (!channel.isOpen()) {
      throw new IllegalStateException("Connection is closed");
    }
    return channel.write(obj);
  }

  void setCurrentRequest(DefaultHttpClientRequest req) {
    if (currentRequest != null) {
      throw new IllegalStateException("Connection is already writing a request");
    }
    this.currentRequest = req;
    this.requests.add(req);
  }

  void endRequest() {
    if (currentRequest == null) {
      throw new IllegalStateException("No write in progress");
    }
    currentRequest = null;

    if (keepAlive) {
      //Close just returns connection to the pool
      close();
    } else {
      //The connection gets closed after the response is received
    }
  }
}
