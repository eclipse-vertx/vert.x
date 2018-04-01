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
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxNetHandler;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Queue;

import static io.vertx.core.http.HttpHeaders.ACCEPT_ENCODING;
import static io.vertx.core.http.HttpHeaders.CLOSE;
import static io.vertx.core.http.HttpHeaders.CONNECTION;
import static io.vertx.core.http.HttpHeaders.DEFLATE_GZIP;
import static io.vertx.core.http.HttpHeaders.HOST;
import static io.vertx.core.http.HttpHeaders.KEEP_ALIVE;
import static io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Http1xClientConnection extends Http1xConnectionBase implements HttpClientConnection {

  private static final Logger log = LoggerFactory.getLogger(Http1xClientConnection.class);

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final String peerHost;
  private final String host;
  private final int port;
  private final Object endpointMetric;
  private final HttpClientMetrics metrics;
  private final HttpVersion version;

  private WebSocketClientHandshaker handshaker;
  private WebSocketImpl ws;

  private final Deque<StreamImpl> pending = new ArrayDeque<>();
  private final Deque<StreamImpl> inflight = new ArrayDeque<>();
  private StreamImpl currentRequest;
  private StreamImpl currentResponse;

  private boolean paused;
  private Buffer pausedChunk;
  private boolean initialized;
  private int keepAliveTimeout;

  Http1xClientConnection(ConnectionListener<HttpClientConnection> listener,
                         HttpVersion version,
                         HttpClientImpl client,
                         Object endpointMetric,
                         ChannelHandlerContext channel,
                         boolean ssl,
                         String peerHost,
                         String host,
                         int port,
                         ContextInternal context,
                         HttpClientMetrics metrics) {
    super(client.getVertx(), channel, context);
    this.listener = listener;
    this.client = client;
    this.options = client.getOptions();
    this.ssl = ssl;
    this.peerHost = peerHost;
    this.host = host;
    this.port = port;
    this.metrics = metrics;
    this.version = version;
    this.endpointMetric = endpointMetric;
    this.keepAliveTimeout = options.getKeepAliveTimeout();
  }

  private static class StreamImpl implements HttpClientStream {

    private final Http1xClientConnection conn;
    private final Handler<AsyncResult<HttpClientStream>> handler;
    private HttpClientRequestImpl request;
    private HttpClientResponseImpl response;
    private boolean requestEnded;
    private boolean responseEnded;
    private boolean reset;
    private boolean close;
    private boolean upgraded;

    StreamImpl(Http1xClientConnection conn, Handler<AsyncResult<HttpClientStream>> handler) {
      this.conn = conn;
      this.handler = handler;
    }

    @Override
    public int id() {
      return -1;
    }

    @Override
    public HttpVersion version() {
      return conn.version;
    }

    @Override
    public HttpClientConnection connection() {
      return conn;
    }

    @Override
    public Context getContext() {
      return conn.context;
    }

    private HttpRequest createRequest(HttpVersion version, HttpMethod method, String rawMethod, String uri, MultiMap headers) {
      DefaultHttpRequest request = new DefaultHttpRequest(HttpUtils.toNettyHttpVersion(version), HttpUtils.toNettyHttpMethod(method, rawMethod), uri, false);
      if (headers != null) {
        for (Map.Entry<String, String> header : headers) {
          // Todo : multi valued headers
          request.headers().add(header.getKey(), header.getValue());
        }
      }
      return request;
    }

    private void prepareHeaders(HttpRequest request, String hostHeader, boolean chunked) {
      HttpHeaders headers = request.headers();
      headers.remove(TRANSFER_ENCODING);
      if (!headers.contains(HOST)) {
        request.headers().set(HOST, hostHeader);
      }
      if (chunked) {
        HttpUtil.setTransferEncodingChunked(request, true);
      }
      if (conn.options.isTryUseCompression() && request.headers().get(ACCEPT_ENCODING) == null) {
        // if compression should be used but nothing is specified by the user support deflate and gzip.
        request.headers().set(ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      if (!conn.options.isKeepAlive() && conn.options.getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_1) {
        request.headers().set(CONNECTION, CLOSE);
      } else if (conn.options.isKeepAlive() && conn.options.getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_0) {
        request.headers().set(CONNECTION, KEEP_ALIVE);
      }
    }

    public void writeHead(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked) {
      HttpRequest request = createRequest(conn.version, method, rawMethod, uri, headers);
      prepareHeaders(request, hostHeader, chunked);
      conn.writeToChannel(request);
    }

    public void writeHeadWithContent(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf buf, boolean end) {
      HttpRequest request = createRequest(conn.version, method, rawMethod, uri, headers);
      prepareHeaders(request, hostHeader, chunked);
      if (end) {
        if (buf != null) {
          conn.writeToChannel(new AssembledFullHttpRequest(request, buf));
        } else {
          conn.writeToChannel(new AssembledFullHttpRequest(request));
        }
      } else {
        conn.writeToChannel(new AssembledHttpRequest(request, buf));
      }
    }

    @Override
    public void writeBuffer(ByteBuf buff, boolean end) {
      if (end) {
        if (buff != null && buff.isReadable()) {
          conn.writeToChannel(new DefaultLastHttpContent(buff, false));
        } else {
          conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT);
        }
      } else if (buff != null) {
        conn.writeToChannel(new DefaultHttpContent(buff));
      }
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      throw new IllegalStateException("Cannot write an HTTP/2 frame over an HTTP/1.x connection");
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
      conn.doSetWriteQueueMaxSize(size);
    }

    @Override
    public boolean isNotWritable() {
      return conn.isNotWritable();
    }

    @Override
    public void doPause() {
      conn.doPause();
    }

    @Override
    public void doResume() {
      conn.doResume();
    }

    @Override
    public void reset(long code) {
      synchronized (conn) {
        if (request == null) {
          throw new IllegalStateException("Sanity check");
        }
        if (!reset) {
          reset = true;
          if (!responseEnded) {
            conn.close();
          }
        }
      }
    }

    @Override
    public void beginRequest(HttpClientRequestImpl req) {
      synchronized (conn) {
        if (conn.currentRequest != this) {
          throw new IllegalStateException("Connection is already writing another request");
        }
        request = req;
        if (conn.metrics != null) {
          Object reqMetric = conn.metrics.requestBegin(conn.endpointMetric, conn.metric(), conn.localAddress(), conn.remoteAddress(), request);
          request.metric(reqMetric);
        }
        conn.inflight.add(conn.currentRequest);
      }
    }

    public void endRequest() {
      StreamImpl next;
      synchronized (conn) {
        if (conn.currentRequest != this) {
          throw new IllegalStateException("No write in progress");
        }
        if (conn.metrics != null) {
          conn.metrics.requestEnd(conn.currentRequest.request.metric());
        }
        requestEnded = true;
        // Should take care of pending list ????
        checkLifecycle();
        // Check pipelined pending request
        next = conn.currentRequest = conn.pending.poll();
        if (next == null) {
          return;
        }
      }
      // Should trampoline ?
      next.handler.handle(Future.succeededFuture(next));
    }

    @Override
    public NetSocket createNetSocket() {
      synchronized (conn) {
        if (responseEnded) {
          throw new IllegalStateException("Request already ended");
        }
        if (upgraded) {
          throw new IllegalStateException("Request already upgraded to NetSocket");
        }
        upgraded = true;


        // connection was upgraded to raw TCP socket
        NetSocketImpl socket = new NetSocketImpl(conn.vertx, conn.chctx, conn.context, conn.client.getSslHelper(), conn.metrics);
        socket.metric(conn.metric());

        // Flush out all pending data
        conn.endReadAndFlush();

        // remove old http handlers and replace the old handler with one that handle plain sockets
        ChannelPipeline pipeline = conn.chctx.pipeline();
        ChannelHandler inflater = pipeline.get(HttpContentDecompressor.class);
        if (inflater != null) {
          pipeline.remove(inflater);
        }
        pipeline.remove("codec");
        pipeline.replace("handler", "handler",  new VertxNetHandler(socket) {
          @Override
          public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
            if (msg instanceof HttpContent) {
              if (msg instanceof LastHttpContent) {
                endResponse((LastHttpContent) msg);
              }
              ReferenceCountUtil.release(msg);
              return;
            }
            super.channelRead(chctx, msg);
          }
          @Override
          protected void handleMessage(NetSocketImpl connection, Object msg) {
            ByteBuf buf = (ByteBuf) msg;
            connection.handleMessageReceived(buf);
          }
        }.removeHandler(sock -> conn.listener.onDiscard()));

        return socket;
      }
    }

    HttpClientResponseImpl beginResponse(HttpResponse resp) {
      if (conn.metrics != null) {
        conn.metrics.responseBegin(request.metric(), response);
      }
      if (resp.status().code() != 100 && request.method() != io.vertx.core.http.HttpMethod.CONNECT) {
        // See https://tools.ietf.org/html/rfc7230#section-6.3
        String responseConnectionHeader = resp.headers().get(HttpHeaders.Names.CONNECTION);
        io.vertx.core.http.HttpVersion protocolVersion = conn.options.getProtocolVersion();
        String requestConnectionHeader = request.headers().get(HttpHeaders.Names.CONNECTION);
        // We don't need to protect against concurrent changes on forceClose as it only goes from false -> true
        if (HttpHeaders.Values.CLOSE.equalsIgnoreCase(responseConnectionHeader) || HttpHeaders.Values.CLOSE.equalsIgnoreCase(requestConnectionHeader)) {
          // In all cases, if we have a close connection option then we SHOULD NOT treat the connection as persistent
          close = true;
        } else if (protocolVersion == io.vertx.core.http.HttpVersion.HTTP_1_0 && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(responseConnectionHeader)) {
          // In the HTTP/1.0 case both request/response need a keep-alive connection header the connection to be persistent
          // currently Vertx forces the Connection header if keepalive is enabled for 1.0
          close = true;
        }
        String keepAliveHeader = resp.headers().get(HttpHeaderNames.KEEP_ALIVE);
        if (keepAliveHeader != null) {
          int timeout = HttpUtils.parseKeepAliveHeaderTimeout(keepAliveHeader);
          if (timeout != -1) {
            conn.keepAliveTimeout = timeout;
          }
        }
      }
      HttpVersion version = HttpUtils.toVertxHttpVersion(resp.protocolVersion());
      if (version != null) {
        return response = new HttpClientResponseImpl(request, version, this, resp.status().code(), resp.status().reasonPhrase(), new HeadersAdaptor(resp.headers()));
      } else {
        return null;
      }
    }

    void endResponse(LastHttpContent trailer) {
      synchronized (conn) {
        if (conn.metrics != null) {
          HttpClientRequestBase req = request;
          Object reqMetric = req.metric();
          if (req.exceptionOccurred != null) {
            conn.metrics.requestReset(reqMetric);
          } else {
            conn.metrics.responseEnd(reqMetric, response);
          }
        }

        Buffer last = conn.pausedChunk;
        conn.pausedChunk = null;
        if (response != null) {
          response.handleEnd(last, new HeadersAdaptor(trailer.trailingHeaders()));
        }

        // Also we keep the connection open for an HTTP CONNECT
        responseEnded = true;
        if (!conn.options.isKeepAlive()) {
          close = true;
        }
        checkLifecycle();
      }
    }

    void checkLifecycle() {
      if (requestEnded && responseEnded) {
        if (upgraded) {
          // Do nothing
        } else if (close) {
          conn.close();
        } else {
          conn.recycle();
        }
      }
    }
  }

  void handleResponse(HttpResponse resp) {
    HttpClientResponseImpl response;
    HttpClientRequestImpl request;
    synchronized (this) {
      StreamImpl requestForResponse;
      if (resp.status().code() == 100) {
        //If we get a 100 continue it will be followed by the real response later, so we don't remove it yet
        requestForResponse = inflight.peek();
      } else {
        requestForResponse = inflight.poll();
      }
      if (requestForResponse == null) {
        throw new IllegalStateException("No response handler");
      }
      currentResponse = requestForResponse;
      response = currentResponse.beginResponse(resp);
      request = currentResponse.request;
    }
    if (response != null) {
      request.handleResponse(response);
    } else {
      request.handleException(new IllegalStateException("Unsupported HTTP version: " + resp.protocolVersion()));
    }
  }

  void handleResponseChunk(Buffer buff) {
    HttpClientResponseImpl resp;
    synchronized (this) {
      if (paused) {
        if (pausedChunk == null) {
          pausedChunk = buff.copy();
        } else {
          pausedChunk.appendBuffer(buff);
        }
        return;
      } else {
        if (pausedChunk != null) {
          buff = pausedChunk.appendBuffer(buff);
          pausedChunk = null;
        }
        resp = currentResponse.response;
        if (resp == null) {
          return;
        }
      }
    }
    resp.handleChunk(buff);
  }

  void handleResponseEnd(LastHttpContent trailer) {
    synchronized (this) {
      StreamImpl resp = currentResponse;
      currentResponse = null;
      // We don't signal response end for a 100-continue response as a real response will follow
      if (resp.response == null || resp.response.statusCode() != 100) {
        resp.endResponse(trailer);
      }
    }
  }

  public HttpClientMetrics metrics() {
    return metrics;
  }

  synchronized void toWebSocket(String requestURI, MultiMap headers, WebsocketVersion vers, String subProtocols,
                   int maxWebSocketFrameSize, Handler<WebSocket> wsConnect) {
    if (ws != null) {
      throw new IllegalStateException("Already websocket");
    }

    try {
      URI wsuri = new URI(requestURI);
      if (!wsuri.isAbsolute()) {
        // Netty requires an absolute url
        wsuri = new URI((ssl ? "https:" : "http:") + "//" + host + ":" + port + requestURI);
      }
      WebSocketVersion version =
         WebSocketVersion.valueOf((vers == null ?
           WebSocketVersion.V13 : vers).toString());
      HttpHeaders nettyHeaders;
      if (headers != null) {
        nettyHeaders = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry: headers) {
          nettyHeaders.add(entry.getKey(), entry.getValue());
        }
      } else {
        nettyHeaders = null;
      }
      handshaker = WebSocketClientHandshakerFactory.newHandshaker(wsuri, version, subProtocols, false,
                                                                  nettyHeaders, maxWebSocketFrameSize,!options.isSendUnmaskedFrames(),false);
      ChannelPipeline p = chctx.pipeline();
      p.addBefore("handler", "handshakeCompleter", new HandshakeInboundHandler(wsConnect, version != WebSocketVersion.V00));
      handshaker.handshake(chctx.channel()).addListener(future -> {
        Handler<Throwable> handler = exceptionHandler();
        if (!future.isSuccess() && handler != null) {
          handler.handle(future.cause());
        }
      });
    } catch (Exception e) {
      handleException(e);
    }
  }

  private final class HandshakeInboundHandler extends ChannelInboundHandlerAdapter {

    private final boolean supportsContinuation;
    private final Handler<WebSocket> wsConnect;
    private final ContextInternal context;
    private final Queue<Object> buffered = new ArrayDeque<>();
    private FullHttpResponse response;
    private boolean handshaking = true;

    public HandshakeInboundHandler(Handler<WebSocket> wsConnect, boolean supportsContinuation) {
      this.supportsContinuation = supportsContinuation;
      this.wsConnect = wsConnect;
      this.context = vertx.getContext();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      super.channelInactive(ctx);
      // if still handshaking this means we not got any response back from the server and so need to notify the client
      // about it as otherwise the client would never been notified.
      if (handshaking) {
        handleException(new WebSocketHandshakeException("Connection closed while handshake in process"));
      }
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      if (handshaker != null && handshaking) {
        if (msg instanceof HttpResponse) {
          HttpResponse resp = (HttpResponse) msg;
          HttpResponseStatus status = resp.status();
          if (status.code() != 101) {
            handshaker = null;
            close();
            handleException(new WebsocketRejectedException(status.code()));
            return;
          }
          response = new DefaultFullHttpResponse(resp.protocolVersion(), status);
          response.headers().add(resp.headers());
        }

        if (msg instanceof HttpContent) {
          if (response != null) {
            response.content().writeBytes(((HttpContent) msg).content());
            if (msg instanceof LastHttpContent) {
              response.trailingHeaders().add(((LastHttpContent) msg).trailingHeaders());
              try {
                handshakeComplete(ctx, response);
                chctx.pipeline().remove(HandshakeInboundHandler.this);
                for (; ; ) {
                  Object m = buffered.poll();
                  if (m == null) {
                    break;
                  }
                  ctx.fireChannelRead(m);
                }
              } catch (WebSocketHandshakeException e) {
                close();
                handleException(e);
              }
            }
          }
        }
      } else {
        buffered.add(msg);
      }
    }

    private void handleException(Exception e) {
      handshaking = false;
      buffered.clear();
      Handler<Throwable> handler = exceptionHandler();
      if (handler != null) {
        context.executeFromIO(v -> {
          handler.handle(e);
        });
      } else {
        log.error("Error in websocket handshake", e);
      }
    }

    private void handshakeComplete(ChannelHandlerContext ctx, FullHttpResponse response) {
      handshaking = false;
      ChannelHandler handler = ctx.pipeline().get(HttpContentDecompressor.class);
      if (handler != null) {
        // remove decompressor as its not needed anymore once connection was upgraded to websockets
        ctx.pipeline().remove(handler);
      }
      WebSocketImpl webSocket = new WebSocketImpl(vertx, Http1xClientConnection.this, supportsContinuation,
                                                  options.getMaxWebsocketFrameSize(),
                                                  options.getMaxWebsocketMessageSize());
      ws = webSocket;
      handshaker.finishHandshake(chctx.channel(), response);
      ws.subProtocol(handshaker.actualSubprotocol());
      context.executeFromIO(v -> {
        log.debug("WebSocket handshake complete");
        if (metrics != null ) {
          webSocket.setMetric(metrics.connected(endpointMetric, metric(), webSocket));
        }
        webSocket.registerHandler(vertx.eventBus());
        wsConnect.handle(webSocket);
      });
    }
  }

  @Override
  public synchronized void handleInterestedOpsChanged() {
    if (!isNotWritable()) {
      if (currentRequest != null) {
        currentRequest.request.handleDrained();
      } else if (ws != null) {
        ws.writable();
      }
    }
  }

  public void doPause() {
    super.doPause();
    paused = true;
  }

  public void doResume() {
    super.doResume();
    paused = false;
    if (pausedChunk != null) {
      context.runOnContext(v -> {
        if (pausedChunk != null) {
          Buffer chunk = pausedChunk;
          pausedChunk = null;
          currentResponse.response.handleChunk(chunk);
        }
      });
    }
  }

  synchronized void handleWsFrame(WebSocketFrameInternal frame) {
    if (ws != null) {
      ws.handleFrame(frame);
    }
  }

  private void retryPending() {
    StreamImpl stream;
    while ((stream = pending.poll()) != null) {
      Handler<AsyncResult<HttpClientStream>> handler = stream.handler;
      client.getConnectionForRequest(peerHost, ssl, port, host, ar1 -> {
        if (ar1.succeeded()) {
          HttpClientConnection conn = ar1.result();
          conn.createStream(ar2 -> {
            if (ar2.succeeded()) {
              handler.handle(Future.succeededFuture(ar2.result()));
            } else {
              handler.handle(Future.failedFuture(ar2.cause()));
            }
          });
        } else {
          handler.handle(Future.failedFuture(ar1.cause()));
        }
      });
    }
  }

  protected synchronized void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }

    retryPending();

    Exception e = new VertxException("Connection was closed");

    // Signal requests failed
    if (metrics != null) {
      for (StreamImpl req: inflight) {
        metrics.requestReset(req.request.metric());
      }
      if (currentResponse != null) {
        metrics.requestReset(currentResponse.request.metric());
      }
    }

    // Connection was closed - call exception handlers for any requests in the pipeline or one being currently written
    for (StreamImpl req: inflight) {
      if (req != currentRequest) {
        req.request.handleException(e);
      }
    }
    if (currentRequest != null) {
      currentRequest.request.handleException(e);
    } else if (currentResponse != null && currentResponse.response != null) {
      currentResponse.response.handleException(e);
    }
  }

  @Override
  protected synchronized void handleException(Throwable e) {
    super.handleException(e);
    retryPending();
    if (currentRequest != null) {
      currentRequest.request.handleException(e);
    } else {
      StreamImpl req = inflight.poll();
      if (req != null) {
        req.request.handleException(e);
      } else if (currentResponse != null && currentResponse.response != null) {
        currentResponse.response.handleException(e);
      }
    }
  }

  @Override
  public synchronized void close() {
    listener.onDiscard();
    if (handshaker == null) {
      super.close();
    } else {
      // make sure everything is flushed out on close
      endReadAndFlush();
      // close the websocket connection by sending a close frame with specified payload.
      handshaker.close(chctx.channel(), new CloseWebSocketFrame(true, 0, 1000, null));
    }
  }

  @Override
  public void closeWithPayload(ByteBuf byteBuf) {
    listener.onDiscard();
    if (handshaker == null) {
      super.close();
    } else {
      // make sure everything is flushed out on close
      endReadAndFlush();
      // close the websocket connection by sending a close frame with specified payload.
      handshaker.close(chctx.channel(), new CloseWebSocketFrame(true, 0, byteBuf));
    }
  }

  @Override
  public void createStream(Handler<AsyncResult<HttpClientStream>> handler) {
    StreamImpl stream = new StreamImpl(this, handler);
    synchronized (this) {
      if (currentRequest != null) {
        pending.add(stream);
        return;
      }
      this.currentRequest = stream;
    }
    handler.handle(Future.succeededFuture(currentRequest));
  }

  @Override
  public void recycle() {
    long expiration = keepAliveTimeout == 0 ? 0L : System.currentTimeMillis() + keepAliveTimeout * 1000;
    listener.onRecycle(expiration);
  }

  @Override
  public synchronized boolean checkInitialized() {
    boolean ret = initialized;
    initialized = true;
    return ret;
  }
}
