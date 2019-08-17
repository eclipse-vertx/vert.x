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
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.impl.InboundBuffer;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

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
class Http1xClientConnection extends Http1xConnectionBase<WebSocketImpl> implements HttpClientConnection {

  private static final Logger log = LoggerFactory.getLogger(Http1xClientConnection.class);

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final SocketAddress server;
  private final Object endpointMetric;
  private final HttpClientMetrics metrics;
  private final HttpVersion version;

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
                         SocketAddress server,
                         ContextInternal context,
                         HttpClientMetrics metrics) {
    super(client.getVertx(), channel, context);
    this.listener = listener;
    this.client = client;
    this.options = client.getOptions();
    this.ssl = ssl;
    this.server = server;
    this.metrics = metrics;
    this.version = version;
    this.endpointMetric = endpointMetric;
    this.keepAliveTimeout = options.getKeepAliveTimeout();
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

      @Override
      public synchronized void handleMessage(Object msg) {
        if (msg instanceof HttpContent) {
          if (msg instanceof LastHttpContent) {
            stream.endResponse((LastHttpContent) msg);
          }
          ReferenceCountUtil.release(msg);
          return;
        }
        super.handleMessage(msg);
      }

      @Override
      protected void handleClosed() {
        listener.onEvict();
        super.handleClosed();
      }
    };
    socket.metric(metric());

    // Flush out all pending data
    flush();

    // remove old http handlers and replace the old handler with one that handle plain sockets
    ChannelPipeline pipeline = chctx.pipeline();
    ChannelHandler inflater = pipeline.get(HttpContentDecompressor.class);
    if (inflater != null) {
      pipeline.remove(inflater);
    }
    pipeline.replace("handler", "handler", VertxHandler.create(socket));

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
    private final ContextInternal context;
    private final Promise<HttpClientStream> promise;
    private final InboundBuffer<Object> queue;
    private HttpClientRequestImpl request;
    private Handler<Void> continueHandler;
    private HttpClientResponseImpl response;
    private boolean requestEnded;
    private boolean responseEnded;
    private boolean reset;
    private StreamImpl next;
    private Object trace;
    private long bytesWritten;
    private long bytesRead;
    private Object metric;

    StreamImpl(ContextInternal context, Http1xClientConnection conn, int id, Handler<AsyncResult<HttpClientStream>> handler) {
      this.context = context;
      this.conn = conn;
      this.promise = Promise.promise();
      this.id = id;
      this.queue = new InboundBuffer<>(context, 5);

      promise.future().setHandler(handler);
    }

    private void append(StreamImpl s) {
      StreamImpl c = this;
      while (c.next != null) {
        c = c.next;
      }
      c.next = s;
    }

    @Override
    public int id() {
      return id;
    }

    @Override
    public Object metric() {
      return metric;
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
    public ContextInternal getContext() {
      return context;
    }

    @Override
    public void writeHead(HttpMethod method, String rawMethod, String uri, MultiMap headers, String hostHeader, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, Handler<Void> contHandler, Handler<AsyncResult<Void>> handler) {
      HttpRequest request = createRequest(method, rawMethod, uri, headers);
      prepareRequestHeaders(request, hostHeader, chunked);
      if (buf != null) {
        bytesWritten += buf.readableBytes();
      }
      continueHandler = contHandler;
      sendRequest(request, buf, end, handler);
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
      HttpRequest request, ByteBuf buf, boolean end, Handler<AsyncResult<Void>> handler) {
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
      conn.writeToChannel(request, conn.toPromise(handler));
    }

    private boolean handleChunk(Buffer buff) {
      bytesRead += buff.length();
      return queue.write(buff);
    }

    @Override
    public void writeBuffer(ByteBuf buff, boolean end, Handler<AsyncResult<Void>> handler) {
      if (buff == null && !end) {
        return;
      }
      HttpContent msg;
      if (end) {
        if (buff != null && buff.isReadable()) {
          msg = new DefaultLastHttpContent(buff, false);
        } else {
          msg = LastHttpContent.EMPTY_LAST_CONTENT;
        }
      } else {
        msg = new DefaultHttpContent(buff);
      }
      bytesWritten += msg.content().readableBytes();
      conn.writeToChannel(msg, conn.toPromise(handler));
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
      queue.fetch(amount);
    }

    @Override
    public void reset(Throwable cause) {
      synchronized (conn) {
        if (reset) {
          return;
        }
        reset = true;
      }
      handleException(cause);
      synchronized (conn) {
        if (conn.requestInProgress == this) {
          if (request == null) {
            // Is that possible in practice ???
            conn.handleRequestEnd(true);
          } else {
            conn.close();
          }
        } else if (!responseEnded) {
          conn.close();
        } else {
          // ????
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
          metric = conn.metrics.requestBegin(conn.endpointMetric, conn.metric(), conn.localAddress(), conn.remoteAddress(), request);
        }
        VertxTracer tracer = context.tracer();
        if (tracer != null) {
          List<Map.Entry<String, String>> tags = new ArrayList<>();
          tags.add(new AbstractMap.SimpleEntry<>("http.url", req.absoluteURI()));
          tags.add(new AbstractMap.SimpleEntry<>("http.method", req.method().name()));
          BiConsumer<String, String> headers = (key, val) -> req.headers().add(key, val);
          trace = tracer.sendRequest(context, request, request.method.name(), headers, HttpUtils.CLIENT_REQUEST_TAG_EXTRACTOR);
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
          conn.metrics.requestEnd(metric);
        }
        doRecycle = responseEnded;
      }
      conn.reportBytesWritten(bytesWritten);
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

    @Override
    public StreamPriority priority() {
      return null;
    }

    @Override
    public void updatePriority(StreamPriority streamPriority) {
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
        conn.metrics.responseBegin(metric, response);
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
      queue.handler(item -> {
        if (item instanceof MultiMap) {
          conn.reportBytesRead(bytesRead);
          response.handleEnd((MultiMap) item);
        } else {
          response.handleChunk((Buffer) item);
        }
      });
      queue.drainHandler(v -> {
        if (!responseEnded) {
          conn.doResume();
        }
      });
      return response;
    }

    private boolean endResponse(LastHttpContent trailer) {
      synchronized (conn) {
        if (conn.metrics != null) {
          conn.metrics.responseEnd(metric, response);
        }
        VertxTracer tracer = context.tracer();
        if (tracer != null) {
          tracer.receiveResponse(context, response, trace, null, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
        }
      }
      queue.write(new HeadersAdaptor(trailer.trailingHeaders()));
      synchronized (conn) {
        responseEnded = true;
        conn.close |= !conn.options.isKeepAlive();
        conn.doResume();
        return requestEnded;
      }
    }

    void handleException(Throwable cause) {
      HttpClientRequestImpl request;
      HttpClientResponseImpl response;
      Promise<HttpClientStream> promise;
      boolean requestEnded;
      synchronized (conn) {
        request = this.request;
        response = this.response;
        promise = this.promise;
        requestEnded = this.requestEnded;
      }
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
        promise.tryFail(cause);
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
      handleWsFrame((WebSocketFrame) msg);
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
        Buffer buff = Buffer.buffer(VertxHandler.safeBuffer(chunk.content(), chctx.alloc()));
        handleResponseChunk(buff);
      }
      if (chunk instanceof LastHttpContent) {
        handleResponseEnd((LastHttpContent) chunk);
      }
    }
  }

  private void handleResponseBegin(HttpResponse resp) {
    if (resp.status().code() == 100) {
      Handler<Void> handler;
      ContextInternal ctx;
      synchronized (this) {
        StreamImpl stream = responseInProgress;
        handler = stream.continueHandler;
        ctx = stream.context;
      }
      if (handler != null) {
        ctx.dispatch(null, handler);
      }
    } else {
      StreamImpl stream;
      HttpClientResponseImpl response;
      HttpClientRequestImpl request;
      synchronized (this) {
        stream = responseInProgress;
        request = stream.request;
        response = stream.beginResponse(resp);
      }
      stream.context.dispatch(v -> request.handleResponse(response));
    }
  }

  private void handleResponseChunk(Buffer buff) {
    StreamImpl resp;
    synchronized (this) {
      resp = responseInProgress;
    }
    if (resp != null) {
      resp.context.dispatch(v -> {
        if (!resp.handleChunk(buff)) {
          doPause();
        }
      });
    }
  }

  private void handleResponseEnd(LastHttpContent trailer) {
    StreamImpl stream;
    synchronized (this) {
      stream = responseInProgress;
      if (stream.response == null) {
        // 100-continue
        return;
      }
      responseInProgress = stream.next;
    }
    stream.context.dispatch(v -> {
      if (stream.endResponse(trailer)) {
        checkLifecycle();
      }
    });
  }

  private void handleRequestEnd(boolean recycle) {
    StreamImpl next;
    synchronized (this) {
      next = requestInProgress.next;
      requestInProgress = next;
    }
    if (recycle) {
      recycle();
    }
    if (next != null) {
      next.promise.complete(next);
    }
  }

  public HttpClientMetrics metrics() {
    return metrics;
  }

  synchronized void toWebSocket(
    String requestURI,
    MultiMap headers,
    WebsocketVersion vers,
    List<String> subProtocols,
    int maxWebSocketFrameSize,
    Handler<AsyncResult<WebSocket>> wsHandler) {
    try {
      URI wsuri = new URI(requestURI);
      if (!wsuri.isAbsolute()) {
        // Netty requires an absolute url
        wsuri = new URI((ssl ? "https:" : "http:") + "//" + server.host() + ":" + server.port() + requestURI);
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

      String subp = null;
      if (subProtocols != null) {
        subp = String.join(",", subProtocols);
      }
      WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
        wsuri,
        version,
        subp,
        !extensionHandshakers.isEmpty(),
        nettyHeaders,
        maxWebSocketFrameSize,
        !options.isSendUnmaskedFrames(),
        false,
        -1);

      WebSocketHandshakeInboundHandler handshakeInboundHandler = new WebSocketHandshakeInboundHandler(handshaker, ar -> {
        AsyncResult<WebSocket> wsRes = ar.map(v -> {
          WebSocketImpl w = new WebSocketImpl(Http1xClientConnection.this.getContext(), Http1xClientConnection.this, version != WebSocketVersion.V00,
            options.getMaxWebsocketFrameSize(),
            options.getMaxWebsocketMessageSize());
          w.subProtocol(handshaker.actualSubprotocol());
          return w;
        });
        if (ar.failed()) {
          close();
        } else {
          ws = (WebSocketImpl) wsRes.result();
          ws.registerHandler(vertx.eventBus());

        }
        getContext().executeFromIO(wsRes, res -> {
          if (res.succeeded()) {
            log.debug("WebSocket handshake complete");
            if (metrics != null) {
              ws.setMetric(metrics.connected(endpointMetric, metric(), ws));
            }
          }
          wsHandler.handle(res);
        });
      });
      p.addBefore("handler", "handshakeCompleter", handshakeInboundHandler);
      handshaker
        .handshake(chctx.channel())
        .addListener(f -> {
        if (!f.isSuccess()) {
          wsHandler.handle(Future.failedFuture(f.cause()));
        }
      });
    } catch (Exception e) {
      handleException(e);
    }
  }

  ArrayList<WebSocketClientExtensionHandshaker> initializeWebsocketExtensionHandshakers (HttpClientOptions options) {
    ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = new ArrayList<WebSocketClientExtensionHandshaker>();
    if (options.getTryWebsocketDeflateFrameCompression()) {
      extensionHandshakers.add(new DeflateFrameClientExtensionHandshaker(options.getWebsocketCompressionLevel(),
        false));
    }

    if (options.getTryUsePerMessageWebsocketCompression()) {
      extensionHandshakers.add(new PerMessageDeflateClientExtensionHandshaker(options.getWebsocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        options.getWebsocketCompressionAllowClientNoContext(), options.getWebsocketCompressionRequestServerNoContext()));
    }

    return extensionHandshakers;
  }

  @Override
  public synchronized void handleInterestedOpsChanged() {
    if (!isNotWritable()) {
      StreamImpl current = requestInProgress;
      if (current != null) {
        current.context.dispatch(v -> current.request.handleDrained());
      } else if (ws != null) {
        ws.handleDrained();
      }
    }
  }

  protected void handleClosed() {
    super.handleClosed();
    if (metrics != null) {
      metrics.endpointDisconnected(endpointMetric, metric());
    }
    WebSocketImpl ws;
    VertxTracer tracer = context.tracer();
    List<StreamImpl> list = Collections.emptyList();
    synchronized (this) {
      ws = this.ws;
      for (StreamImpl r = responseInProgress;r != null;r = r.next) {
        if (metrics != null) {
          metrics.requestReset(r.metric);
        }
        if (tracer != null) {
          tracer.receiveResponse(r.context, null, r.trace, ConnectionBase.CLOSED_EXCEPTION, TagExtractor.empty());
        }
        if (list.isEmpty()) {
          list = new ArrayList<>();
        }
        list.add(r);
      }
    }
    if (ws != null) {
      ws.handleClosed();
    }
    for (StreamImpl stream : list) {
      stream.context.dispatch(v -> stream.handleException(CLOSED_EXCEPTION));
    }
  }

  protected void handleIdle() {
    synchronized (this) {
      if (ws == null && responseInProgress == null) {
        return;
      }
    }
    super.handleIdle();
  }

  @Override
  protected synchronized void handleException(Throwable e) {
    super.handleException(e);
    if (ws != null) {
      ws.handleException(e);
    } else {
      for (StreamImpl r = responseInProgress;r != null;r = r.next) {
        r.handleException(e);
      }
    }
  }

  @Override
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    StreamImpl stream;
    synchronized (this) {
      ContextInternal sub = getContext().duplicate(context);
      stream = new StreamImpl(sub, this, seq++, handler);
      if (requestInProgress != null) {
        requestInProgress.append(stream);
        return;
      }
      requestInProgress = stream;
    }
    stream.context.dispatch(Future.succeededFuture(stream), stream.promise);
  }

  private void recycle() {
    long expiration = keepAliveTimeout == 0 ? 0L : System.currentTimeMillis() + keepAliveTimeout * 1000;
    listener.onRecycle(expiration);
  }
}
