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
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Context;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.pool.ConnectionListener;
import io.vertx.core.http.impl.ws.WebSocketFrameInternal;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxNetHandler;
import io.vertx.core.spi.metrics.HttpClientMetrics;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
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

  private static final int ST_NOOP = 0, ST_CLOSE = 1, ST_RECYCLE = 2;

  private static final Logger log = LoggerFactory.getLogger(Http1xClientConnection.class);

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  private final boolean ssl;
  private final String host;
  private final int port;
  private final Object endpointMetric;
  private final boolean pipelining;
  private final boolean keepAlive;
  private final int pipeliningLimit;
  // Requests can be pipelined so we need a queue to keep track of requests
  private final Deque<StreamImpl> inflight = new ArrayDeque<>();
  private final HttpClientMetrics metrics;
  private final HttpVersion version;

  private WebSocketClientHandshaker handshaker;
  private StreamImpl currentRequest;
  private StreamImpl currentResponse;
  private WebSocketImpl ws;

  private boolean paused;
  private Buffer pausedChunk;

  Http1xClientConnection(ConnectionListener<HttpClientConnection> listener,
                         HttpVersion version,
                         HttpClientImpl client,
                         Object endpointMetric,
                         ChannelHandlerContext channel,
                         boolean ssl,
                         String host,
                         int port,
                         ContextImpl context,
                         HttpClientMetrics metrics) {
    super(client.getVertx(), channel, context);
    this.listener = listener;
    this.client = client;
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    this.metrics = metrics;
    this.version = version;
    this.endpointMetric = endpointMetric;
    this.pipelining = client.getOptions().isPipelining();
    this.keepAlive = client.getOptions().isKeepAlive();
    this.pipeliningLimit = client.getOptions().getPipeliningLimit();
  }

  private class StreamImpl implements HttpClientStream {

    private HttpClientRequestImpl request;
    private HttpClientResponseImpl response;
    private boolean reset;
    private int status = ST_NOOP;
    private boolean requestEnded;
    private boolean responseEnded;

    @Override
    public int id() {
      return -1;
    }

    @Override
    public HttpVersion version() {
      return version;
    }

    @Override
    public HttpClientConnection connection() {
      return Http1xClientConnection.this;
    }

    @Override
    public Context getContext() {
      return context;
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
        HttpHeaders.setTransferEncodingChunked(request);
      }
      if (client.getOptions().isTryUseCompression() && request.headers().get(ACCEPT_ENCODING) == null) {
        // if compression should be used but nothing is specified by the user support deflate and gzip.
        request.headers().set(ACCEPT_ENCODING, DEFLATE_GZIP);
      }
      if (!client.getOptions().isKeepAlive() && client.getOptions().getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_1) {
        request.headers().set(CONNECTION, CLOSE);
      } else if (client.getOptions().isKeepAlive() && client.getOptions().getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_0) {
        request.headers().set(CONNECTION, KEEP_ALIVE);
      }
    }

    public void writeHead(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked) {
      HttpRequest request = createRequest(version, method, rawMethod, uri, headers);
      prepareHeaders(request, hostHeader, chunked);
      writeToChannel(request);
    }

    public void writeHeadWithContent(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf buf, boolean end) {
      HttpRequest request = createRequest(version, method, rawMethod, uri, headers);
      prepareHeaders(request, hostHeader, chunked);
      if (end) {
        if (buf != null) {
          writeToChannel(new AssembledFullHttpRequest(request, buf));
        } else {
          writeToChannel(new AssembledFullHttpRequest(request));
        }
      } else {
        writeToChannel(new AssembledHttpRequest(request, buf));
      }
    }

    @Override
    public void writeBuffer(ByteBuf buff, boolean end) {
      if (end) {
        if (buff != null && buff.isReadable()) {
          writeToChannel(new DefaultLastHttpContent(buff, false));
        } else {
          writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT);
        }
      } else if (buff != null) {
        writeToChannel(new DefaultHttpContent(buff));
      }
    }

    @Override
    public void writeFrame(int type, int flags, ByteBuf payload) {
      throw new IllegalStateException("Cannot write an HTTP/2 frame over an HTTP/1.x connection");
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
      Http1xClientConnection.this.doSetWriteQueueMaxSize(size);
    }

    @Override
    public boolean isNotWritable() {
      return Http1xClientConnection.this.isNotWritable();
    }

    @Override
    public void checkDrained() {
      handleInterestedOpsChanged();
    }

    @Override
    public void doPause() {
      Http1xClientConnection.this.doPause();
    }

    @Override
    public void doResume() {
      Http1xClientConnection.this.doResume();
    }

    @Override
    public void reset(long code) {
      if (request == null) {
        throw new IllegalStateException("Sanity check");
      }
      if (!reset) {
        reset = true;
        if (!requestEnded || !responseEnded) {
          close();
        }
      }
    }

    public synchronized void beginRequest(HttpClientRequestImpl req) {
      if (request != null) {
        throw new IllegalStateException("Request already in progress");
      }
      if (currentRequest != this) {
        throw new IllegalStateException("Connection is already writing another request");
      }
      if (metrics != null) {
        Object reqMetric = metrics.requestBegin(endpointMetric, metric(), localAddress(), remoteAddress(), req);
        req.metric(reqMetric);
      }
      request = req;
      currentRequest = this;
      inflight.add(currentRequest);
    }

    public synchronized void endRequest() {
      if (currentRequest != this) {
        throw new IllegalStateException("No write in progress");
      }
      if (metrics != null) {
        metrics.requestEnd(currentRequest.request.metric());
      }
      currentRequest = null;
      requestEnded = true;
      int st;
      if (responseEnded) {
        st = status;
      } else {
        if (pipelining && inflight.size() < pipeliningLimit) {
          st = ST_RECYCLE;
        }  else {
          st = ST_NOOP;
        }
        status = st;
      }
      switch (st) {
        case ST_CLOSE:
          close();
          break;
        case ST_RECYCLE:
          listener.onRecycle();
          break;
      }
    }

    @Override
    public NetSocket createNetSocket() {
      return Http1xClientConnection.this.createNetSocket();
    }

    void handleResponse(HttpResponse resp) {
      io.netty.handler.codec.http.HttpVersion nettyVersion = resp.protocolVersion();
      HttpVersion vertxVersion;
      if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        vertxVersion = HttpVersion.HTTP_1_0;
      } else if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
        vertxVersion = HttpVersion.HTTP_1_1;
      } else {
        vertxVersion = null;
      }
      response = new HttpClientResponseImpl(request, vertxVersion, this, resp.status().code(), resp.status().reasonPhrase(), new HeadersAdaptor(resp.headers()));
      if (metrics != null) {
        metrics.responseBegin(request.metric(), response);
      }
      if (vertxVersion != null) {
        request.handleResponse(response);
      } else {
        request.handleException(new IllegalStateException("Unsupported HTTP version: " + nettyVersion));
      }
    }

    void handleResponseEnd(LastHttpContent trailer) {
      if (metrics != null) {
        HttpClientRequestBase req = request;
        Object reqMetric = req.metric();
        if (req.exceptionOccurred != null) {
          metrics.requestReset(reqMetric);
        } else {
          metrics.responseEnd(reqMetric, response);
        }
      }
      Buffer last = pausedChunk;
      pausedChunk = null;
      response.handleEnd(last, new HeadersAdaptor(trailer.trailingHeaders()));

      int st;
      /* if (reset && inflight.isEmpty()) {
        st = ST_CLOSE;
      } else */ if (keepAlive) {
        if (status == ST_RECYCLE) {
          st = ST_NOOP;
        } else {
          st = ST_RECYCLE;
        }
      } else {
        st = ST_CLOSE;
      }

      // We don't signal response end for a 100-continue response as a real response will follow
      // Also we keep the connection open for an HTTP CONNECT
      if (response.statusCode() != 100 && request.method() != io.vertx.core.http.HttpMethod.CONNECT) {

        // See https://tools.ietf.org/html/rfc7230#section-6.3
        String responseConnectionHeader = response.getHeader(HttpHeaders.Names.CONNECTION);
        io.vertx.core.http.HttpVersion protocolVersion = client.getOptions().getProtocolVersion();
        String requestConnectionHeader = request.headers().get(HttpHeaders.Names.CONNECTION);
        // We don't need to protect against concurrent changes on forceClose as it only goes from false -> true
        if (HttpHeaders.Values.CLOSE.equalsIgnoreCase(responseConnectionHeader) || HttpHeaders.Values.CLOSE.equalsIgnoreCase(requestConnectionHeader)) {
          // In all cases, if we have a close connection option then we SHOULD NOT treat the connection as persistent
          st = ST_CLOSE;
        } else if (protocolVersion == io.vertx.core.http.HttpVersion.HTTP_1_0 && !HttpHeaders.Values.KEEP_ALIVE.equalsIgnoreCase(responseConnectionHeader)) {
          // In the HTTP/1.0 case both request/response need a keep-alive connection header the connection to be persistent
          // currently Vertx forces the Connection header if keepalive is enabled for 1.0
          st = ST_CLOSE;
        }
      }

      responseEnded = true;
      if (requestEnded) {
        switch (st) {
          case ST_CLOSE:
            close();
            break;
          case ST_RECYCLE:
            listener.onRecycle();
            break;
        }
      } else {
        status = st;
      }
    }
  }

  void handleResponse(HttpResponse resp) {
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
    currentResponse.handleResponse(resp);
  }

  void handleResponseChunk(Buffer buff) {
    if (paused) {
      if (pausedChunk == null) {
        pausedChunk = buff.copy();
      } else {
        pausedChunk.appendBuffer(buff);
      }
    } else {
      if (pausedChunk != null) {
        buff = pausedChunk.appendBuffer(buff);
        pausedChunk = null;
      }
      currentResponse.response.handleChunk(buff);
    }
  }

  void handleResponseEnd(LastHttpContent trailer) {
    StreamImpl resp = currentResponse;
    if (resp.response.statusCode() != 100) {
      currentResponse = null;
      resp.handleResponseEnd(trailer);
    }
  }

  @Override
  public Channel channel() {
    return chctx.channel();
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
                                                                  nettyHeaders, maxWebSocketFrameSize,!client.getOptions().isSendUnmaskedFrames(),false);
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
    private final ContextImpl context;
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
        context.executeFromIO(() -> {
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
      // Need to set context before constructor is called as writehandler registration needs this
      ContextImpl.setContext(context);
      WebSocketImpl webSocket = new WebSocketImpl(vertx, Http1xClientConnection.this, supportsContinuation,
                                                  client.getOptions().getMaxWebsocketFrameSize(),
                                                  client.getOptions().getMaxWebsocketMessageSize());
      ws = webSocket;
      handshaker.finishHandshake(chctx.channel(), response);
      ws.subProtocol(handshaker.actualSubprotocol());
      context.executeFromIO(() -> {
        log.debug("WebSocket handshake complete");
        if (metrics != null ) {
          webSocket.setMetric(metrics.connected(endpointMetric, metric(), webSocket));
        }
        wsConnect.handle(webSocket);
      });
    }
  }

  public synchronized boolean isValid() {
    return /*!reset &&*/ chctx.channel().isOpen();
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
      vertx.runOnContext(v -> {
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

  protected synchronized void handleClosed() {
    super.handleClosed();
    if (ws != null) {
      ws.handleClosed();
    }
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
    } else if (currentResponse != null) {
      currentResponse.response.handleException(e);
    }
  }

  public ContextImpl getContext() {
    return super.getContext();
  }

  @Override
  protected synchronized void handleException(Throwable e) {
    super.handleException(e);
    if (currentRequest != null) {
      currentRequest.request.handleException(e);
    } else {
      StreamImpl req = inflight.poll();
      if (req != null) {
        req.request.handleException(e);
      } else if (currentResponse != null) {
        currentResponse.response.handleException(e);
      }
    }
  }

  @Override
  public synchronized void close() {
    if (handshaker == null) {
      super.close();
    } else {
      // make sure everything is flushed out on close
      endReadAndFlush();
      // close the websocket connection by sending a close frame.
      handshaker.close(chctx.channel(), new CloseWebSocketFrame(1000, null));
    }
  }

  public NetSocket createNetSocket() {
    // connection was upgraded to raw TCP socket
    NetSocketImpl socket = new NetSocketImpl(vertx, chctx, context, client.getSslHelper(), metrics);
    socket.metric(metric());
    Map<Channel, NetSocketImpl> connectionMap = new HashMap<>(1);
    connectionMap.put(chctx.channel(), socket);

    // Flush out all pending data
    endReadAndFlush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    ChannelPipeline pipeline = chctx.pipeline();
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
            handleResponseEnd((LastHttpContent) msg);
          }
          ReferenceCountUtil.release(msg);
          return;
        }
        super.channelRead(chctx, msg);
      }
      @Override
      protected void handleMessage(NetSocketImpl connection, ContextImpl context, ChannelHandlerContext chctx, Object msg) throws Exception {
        ByteBuf buf = (ByteBuf) msg;
        connection.handleMessageReceived(buf);
      }
    }.removeHandler(sock -> listener.onClose()));
    return socket;
  }

  @Override
  public HttpClientStream createStream() throws Exception {
    currentRequest = new StreamImpl();
    return currentRequest;
  }
}
