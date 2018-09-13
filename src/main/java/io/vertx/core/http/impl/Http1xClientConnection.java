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
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
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
import io.vertx.core.queue.Queue;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.vertx.core.http.HttpHeaders.*;

/**
 *
 * This class is optimised for performance when used on the same event loop. However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xClientConnection extends Http1xConnectionBase implements HttpClientConnection {

  private static final Logger log = LoggerFactory.getLogger(Http1xClientConnection.class);

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final String host;
  private final int port;
  private final Object endpointMetric;
  private final HttpClientMetrics metrics;
  private final HttpVersion version;

  private WebSocketClientHandshaker handshaker;
  private WebSocketImpl ws;
  private boolean closeFrameSent;

  private StreamImpl requestInProgress;                          // The request being sent
  private StreamImpl responseInProgress;                         // The request waiting for a response

  private boolean close;
  private boolean upgraded;
  private int keepAliveTimeout;
  private int seq = 1;

  Http1xClientConnection(ConnectionListener<HttpClientConnection> listener,
                         HttpVersion version,
                         HttpClientImpl client,
                         Object endpointMetric,
                         ChannelHandlerContext channel,
                         boolean ssl,
                         String host,
                         int port,
                         ContextInternal context,
                         HttpClientMetrics metrics) {
    super(client.getVertx(), channel, context);
    this.listener = listener;
    this.client = client;
    this.options = client.getOptions();
    this.ssl = ssl;
    this.host = host;
    this.port = port;
    this.metrics = metrics;
    this.version = version;
    this.endpointMetric = endpointMetric;
    this.keepAliveTimeout = options.getKeepAliveTimeout();
    this.exceptionHandler(client.exceptionHandler());
  }

  Object endpointMetric() {
    return endpointMetric;
  }

  ConnectionListener<HttpClientConnection> listener() {
    return listener;
  }

  private synchronized NetSocket upgrade(StreamImpl stream) {
    if (options.isPipelining()) {
      throw new IllegalStateException("Cannot upgrade a pipe-lined request");
    }
    if (upgraded) {
      throw new IllegalStateException("Request already upgraded to NetSocket");
    }
    upgraded = true;

    // connection was upgraded to raw TCP socket
    AtomicBoolean paused = new AtomicBoolean(false);
    NetSocketImpl socket = new NetSocketImpl(vertx, chctx, context, client.getSslHelper(), metrics) {
      {
        super.pause();
      }
      @Override
      public synchronized NetSocket handler(Handler<Buffer> dataHandler) {
        return super.handler(dataHandler);
      }
      @Override
      public synchronized NetSocket pause() {
        paused.set(true);
        return super.pause();
      }
      @Override
      public synchronized NetSocket resume() {
        paused.set(false);
        return super.resume();
      }
    };
    socket.metric(metric());

    // Flush out all pending data
    endReadAndFlush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    ChannelPipeline pipeline = chctx.pipeline();
    ChannelHandler inflater = pipeline.get(HttpContentDecompressor.class);
    if (inflater != null) {
      pipeline.remove(inflater);
    }
    pipeline.replace("handler", "handler",  new VertxNetHandler(socket) {
      @Override
      public void channelRead(ChannelHandlerContext chctx, Object msg) throws Exception {
        if (msg instanceof HttpContent) {
          if (msg instanceof LastHttpContent) {
            stream.endResponse((LastHttpContent) msg);
          }
          ReferenceCountUtil.release(msg);
          return;
        }
        super.channelRead(chctx, msg);
      }
    }.removeHandler(sock -> listener.onDiscard()));

    // Removing this codec might fire pending buffers in the HTTP decoder
    // this happens when the channel reads the HTTP response and the following data in a single buffer
    pipeline.remove("codec");

    // Async check to deliver the pending messages
    // because the netSocket access in HttpClientResponse is synchronous
    // we need to pause the NetSocket to avoid losing or reordering buffers
    // and then asynchronously un-pause it unless it was actually paused by the application
    context.runOnContext(v -> {
      if (!paused.get()) {
        socket.resume();
      }
    });

    return socket;
  }

  private static class StreamImpl implements HttpClientStream {

    private final int id;
    private final Http1xClientConnection conn;
    private final Future<HttpClientStream> fut;
    private HttpClientRequestImpl request;
    private HttpClientResponseImpl response;
    private boolean requestEnded;
    private boolean responseEnded;
    private boolean reset;
    private Queue<Buffer> queue;
    private MultiMap trailers;
    private StreamImpl next;

    StreamImpl(Http1xClientConnection conn, int id, Handler<AsyncResult<HttpClientStream>> handler) {
      this.conn = conn;
      this.fut = Future.<HttpClientStream>future().setHandler(handler);
      this.id = id;
      this.queue = Queue.queue(conn.context, 0);
    }

    private void append(StreamImpl s) {
      StreamImpl c = this;
      while (c.next != null) {
        c = c.next;
      }
      c.next = s;
    }

    @Override
    public void reportBytesWritten(long numberOfBytes) {
      conn.reportBytesWritten(numberOfBytes);
    }

    @Override
    public void reportBytesRead(long numberOfBytes) {
      conn.reportBytesRead(numberOfBytes);
    }

    @Override
    public int id() {
      return id;
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

    public void writeHead(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf buf, boolean end) {
      HttpRequest request = createRequest(method, rawMethod, uri, headers);
      prepareRequestHeaders(request, hostHeader, chunked);
      sendRequest(request, buf, end);
      if (conn.responseInProgress == null) {
        conn.responseInProgress = this;
      } else {
        conn.responseInProgress.append(this);
      }
      next = null;
    }

    private HttpRequest createRequest(HttpMethod method, String rawMethod, String uri, MultiMap headers) {
      DefaultHttpRequest request = new DefaultHttpRequest(HttpUtils.toNettyHttpVersion(conn.version), HttpUtils.toNettyHttpMethod(method, rawMethod), uri, false);
      if (headers != null) {
        for (Map.Entry<String, String> header : headers) {
          // Todo : multi valued headers
          request.headers().add(header.getKey(), header.getValue());
        }
      }
      return request;
    }

    private void prepareRequestHeaders(HttpRequest request, String hostHeader, boolean chunked) {
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

    private void sendRequest(
      HttpRequest request, ByteBuf buf, boolean end) {
      if (end) {
        if (buf != null) {
          request = new AssembledFullHttpRequest(request, buf);
        } else {
          request = new AssembledFullHttpRequest(request);
        }
      } else {
        if (buf != null) {
          request = new AssembledHttpRequest(request, buf);
        }
      }
      conn.writeToChannel(request);
    }

    private boolean handleChunk(Buffer buff) {
      return queue.add(buff);
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
      queue.pause();
    }

    @Override
    public void doFetch(long amount) {
      queue.take(amount);
    }

    @Override
    public void doResume() {
      queue.resume();
    }

    @Override
    public void reset(long code) {
      synchronized (conn) {
        if (!reset) {
          reset = true;
          if (conn.requestInProgress == this) {
            if (request == null) {
              conn.requestInProgress = null;
              conn.recycle();
            } else {
              conn.close();
            }
          } else if (!responseEnded) {
            conn.close();
          }
        }
      }
    }

    @Override
    public void beginRequest(HttpClientRequestImpl req) {
      synchronized (conn) {
        if (request != null) {
          throw new IllegalStateException("Already writing a request");
        }
        if (conn.requestInProgress != this) {
          throw new IllegalStateException("Connection is already writing another request");
        }
        request = req;
        if (conn.metrics != null) {
          Object reqMetric = conn.metrics.requestBegin(conn.endpointMetric, conn.metric(), conn.localAddress(), conn.remoteAddress(), request);
          request.metric(reqMetric);
        }
      }
    }

    public void endRequest() {
      boolean doRecycle;
      synchronized (conn) {
        StreamImpl s = conn.requestInProgress;
        if (s != this) {
          throw new IllegalStateException("No write in progress");
        }
        if (requestEnded) {
          throw new IllegalStateException("Request already sent");
        }
        requestEnded = true;
        if (conn.metrics != null) {
          conn.metrics.requestEnd(request.metric());
        }
        doRecycle = responseEnded;
      }
      conn.handleRequestEnd(doRecycle);
    }

    @Override
    public NetSocket createNetSocket() {
      synchronized (conn) {
        if (responseEnded) {
          throw new IllegalStateException("Response already ended");
        }
        return conn.upgrade(this);
      }
    }

    private HttpClientResponseImpl beginResponse(HttpResponse resp) {
      HttpVersion version;
      if (resp.protocolVersion() == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = io.vertx.core.http.HttpVersion.HTTP_1_0;
      } else {
        version = io.vertx.core.http.HttpVersion.HTTP_1_1;
      }
      response = new HttpClientResponseImpl(request, version, this, resp.status().code(), resp.status().reasonPhrase(), new HeadersAdaptor(resp.headers()));
      if (conn.metrics != null) {
        conn.metrics.responseBegin(request.metric(), response);
      }
      if (resp.status().code() != 100 && request.method() != io.vertx.core.http.HttpMethod.CONNECT) {
        // See https://tools.ietf.org/html/rfc7230#section-6.3
        String responseConnectionHeader = resp.headers().get(HttpHeaderNames.CONNECTION);
        io.netty.handler.codec.http.HttpVersion protocolVersion = resp.protocolVersion();
        String requestConnectionHeader = request.headers().get(HttpHeaderNames.CONNECTION);
        // We don't need to protect against concurrent changes on forceClose as it only goes from false -> true
        if (HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(responseConnectionHeader) || HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(requestConnectionHeader)) {
          // In all cases, if we have a close connection option then we SHOULD NOT treat the connection as persistent
          conn.close = true;
        } else if (protocolVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0 && !HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(responseConnectionHeader)) {
          // In the HTTP/1.0 case both request/response need a keep-alive connection header the connection to be persistent
          // currently Vertx forces the Connection header if keepalive is enabled for 1.0
          conn.close = true;
        }
        String keepAliveHeader = resp.headers().get(HttpHeaderNames.KEEP_ALIVE);
        if (keepAliveHeader != null) {
          int timeout = HttpUtils.parseKeepAliveHeaderTimeout(keepAliveHeader);
          if (timeout != -1) {
            conn.keepAliveTimeout = timeout;
          }
        }
      }
      queue.handler(buf -> response.handleChunk(buf));
      queue.emptyHandler(v -> {
        if (responseEnded) {
          response.handleEnd(trailers);
        }
      });
      queue.writableHandler(v -> {
        if (!responseEnded) {
          conn.doResume();
        }
      });
      return response;
    }

    private boolean endResponse(LastHttpContent trailer) {
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
        trailers = new HeadersAdaptor(trailer.trailingHeaders());
        if (queue.isEmpty()) {
          response.handleEnd(trailers);
        }
        responseEnded = true;
        conn.close |= !conn.options.isKeepAlive();
        conn.doResume();
        return requestEnded;
      }
    }

    void handleException(Throwable cause) {
      synchronized (conn) {
        if (request != null) {
          if (response == null) {
            request.handleException(cause);
          } else {
            if (!requestEnded) {
              request.handleException(cause);
            }
            response.handleException(cause);
          }
        } else {
          fut.tryFail(cause);
        }
      }
    }
  }

  private void checkLifecycle() {
    if (upgraded) {
      // Do nothing
    } else if (close) {
      close();
    } else {
      recycle();
    }
  }

  private Throwable validateMessage(Object msg) {
    if (msg instanceof HttpObject) {
      HttpObject obj = (HttpObject) msg;
      DecoderResult result = obj.decoderResult();
      if (result.isFailure()) {
        return result.cause();
      } else if (obj instanceof HttpResponse) {
        io.netty.handler.codec.http.HttpVersion version = ((HttpResponse) obj).protocolVersion();
        if (version != io.netty.handler.codec.http.HttpVersion.HTTP_1_0 && version != io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
          return new IllegalStateException("Unsupported HTTP version: " + version);
        }
      }
    }
    return null;
  }

  public void handleMessage(Object msg) {
    Throwable error = validateMessage(msg);
    if (error != null) {
      fail(error);
    } else if (msg instanceof HttpObject) {
      HttpObject obj = (HttpObject) msg;
      handleHttpMessage(obj);
    } else if (msg instanceof WebSocketFrame) {
      WebSocketFrameInternal frame = decodeFrame((WebSocketFrame) msg);
      switch (frame.type()) {
        case BINARY:
        case CONTINUATION:
        case TEXT:
        case PONG:
          handleWsFrame(frame);
          break;
        case PING:
          // Echo back the content of the PING frame as PONG frame as specified in RFC 6455 Section 5.5.2
          chctx.writeAndFlush(new PongWebSocketFrame(frame.getBinaryData().copy()));
          break;
        case CLOSE:
          handleWsFrame(frame);
          if (!closeFrameSent) {
            // Echo back close frame and close the connection once it was written.
            // This is specified in the WebSockets RFC 6455 Section  5.4.1
            CloseWebSocketFrame closeFrame = new CloseWebSocketFrame(frame.closeStatusCode(), frame.closeReason());
            chctx.writeAndFlush(closeFrame).addListener(ChannelFutureListener.CLOSE);
            closeFrameSent = true;
          }
          break;
        default:
          throw new IllegalStateException("Invalid type: " + frame.type());
      }
    } else {
      throw new IllegalStateException("Invalid object " + msg);
    }
  }

  private void handleHttpMessage(HttpObject obj) {
    if (obj instanceof HttpResponse) {
      handleResponseBegin((HttpResponse) obj);
    } else if (obj instanceof HttpContent) {
      HttpContent chunk = (HttpContent) obj;
      if (chunk.content().isReadable()) {
        Buffer buff = Buffer.buffer(VertxHttpHandler.safeBuffer(chunk.content(), chctx.alloc()));
        handleResponseChunk(buff);
      }
      if (chunk instanceof LastHttpContent) {
        handleResponseEnd((LastHttpContent) chunk);
      }
    }
  }

  private void handleResponseBegin(HttpResponse resp) {
    StreamImpl stream;
    HttpClientResponseImpl response;
    HttpClientRequestImpl request;
    Throwable err;
    synchronized (this) {
      stream = responseInProgress;
      request = stream.request;
      try {
        response = stream.beginResponse(resp);
        err = null;
      } catch (Exception e) {
        err = e;
        response = null;
      }
    }
    if (response != null) {
      request.handleResponse(response);
    } else {
      request.handleException(err);
    }
  }

  private void handleResponseChunk(Buffer buff) {
    StreamImpl resp;
    synchronized (this) {
      resp = responseInProgress;
    }
    if (resp != null) {
      if (!resp.handleChunk(buff)) {
        doPause();
      }
    }
  }

  private void handleResponseEnd(LastHttpContent trailer) {
    StreamImpl stream;
    synchronized (this) {
      stream = responseInProgress;
      // We don't signal response end for a 100-continue response as a real response will follow
      if (stream.response == null || stream.response.statusCode() != 100) {
        responseInProgress = responseInProgress.next;
      }
    }
    if (stream.endResponse(trailer)) {
      checkLifecycle();
    }
  }

  private void handleRequestEnd(boolean recycle) {
    StreamImpl next;
    synchronized (this) {
      next = requestInProgress.next;
      requestInProgress = next;
    }
    if (recycle) {
      checkLifecycle();
    }
    if (next != null) {
      next.fut.complete(next);
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

      ChannelPipeline p = chctx.channel().pipeline();
      ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = initializeWebsocketExtensionHandshakers(client.getOptions());
      if (!extensionHandshakers.isEmpty()) {
        p.addBefore("handler", "websocketsExtensionsHandler", new WebSocketClientExtensionHandler(
          extensionHandshakers.toArray(new WebSocketClientExtensionHandshaker[extensionHandshakers.size()])));
      }

      handshaker = WebSocketClientHandshakerFactory.newHandshaker(wsuri, version, subProtocols, !extensionHandshakers.isEmpty(),
                                                                  nettyHeaders, maxWebSocketFrameSize,!options.isSendUnmaskedFrames(),false);

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

  ArrayList<WebSocketClientExtensionHandshaker> initializeWebsocketExtensionHandshakers (HttpClientOptions options) {
    ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = new ArrayList<WebSocketClientExtensionHandshaker>();
    if (options.tryWebsocketDeflateFrameCompression()) {
      extensionHandshakers.add(new DeflateFrameClientExtensionHandshaker(options.websocketCompressionLevel(),
        false));
    }

    if (options.tryUsePerMessageWebsocketCompression ()) {
      extensionHandshakers.add(new PerMessageDeflateClientExtensionHandshaker(options.websocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        options.getWebsocketCompressionAllowClientNoContext(), options.getWebsocketCompressionRequestServerNoContext()));
    }

    return extensionHandshakers;
  }

  private final class HandshakeInboundHandler extends ChannelInboundHandlerAdapter {

    private final boolean supportsContinuation;
    private final Handler<WebSocket> wsConnect;
    private final ContextInternal context;
    private final Deque<Object> buffered = new ArrayDeque<>();
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
                for (;;) {
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
      if (requestInProgress != null) {
        requestInProgress.request.handleDrained();
      } else if (ws != null) {
        ws.writable();
      }
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
    if (metrics != null) {
      for (StreamImpl r = responseInProgress;r != null;r = r.next) {
        metrics.requestReset(r.request.metric());
      }
    }
    failStreams(CLOSED_EXCEPTION);
  }

  private void failStreams(Throwable cause) {
    for (StreamImpl r = responseInProgress;r != null;r = r.next) {
      r.handleException(cause);
    }
  }

  @Override
  protected synchronized void handleException(Throwable e) {
    super.handleException(e);
    if (ws != null) {
      ws.handleException(e);
    } else {
      failStreams(e);
    }
  }

  @Override
  public synchronized void close() {
    closeWithPayload(null);
  }

  @Override
  public void closeWithPayload(ByteBuf byteBuf) {
    if (handshaker == null) {
      super.close();
    } else {
      // make sure everything is flushed out on close
      endReadAndFlush();
      // close the websocket connection by sending a close frame with specified payload.
      CloseWebSocketFrame closeFrame;
      if (byteBuf != null) {
        closeFrame = new CloseWebSocketFrame(true, 0, byteBuf);
      } else {
        closeFrame = new CloseWebSocketFrame(true, 0, 1000, null);
      }
      handshaker.close(chctx.channel(), closeFrame);
    }
  }

  @Override
  public void createStream(Handler<AsyncResult<HttpClientStream>> handler) {
    StreamImpl stream;
    synchronized (this) {
      stream = new StreamImpl(this, seq++, handler);
      if (requestInProgress != null) {
        requestInProgress.append(stream);
        return;
      }
      requestInProgress = stream;
    }
    stream.fut.complete(stream);
  }

  private void recycle() {
    long expiration = keepAliveTimeout == 0 ? 0L : System.currentTimeMillis() + keepAliveTimeout * 1000;
    listener.onRecycle(expiration);
  }
}
