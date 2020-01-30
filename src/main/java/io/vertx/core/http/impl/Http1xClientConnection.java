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
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
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

  private static final Handler<Object> INVALID_MSG_HANDLER = msg -> {
    throw new IllegalStateException("Invalid object " + msg);
  };

  private final ConnectionListener<HttpClientConnection> listener;
  private final HttpClientImpl client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final SocketAddress server;
  private final Object endpointMetric;
  private final HttpClientMetrics metrics;
  private final HttpVersion version;

  private Deque<Stream> requests = new ArrayDeque<>();
  private Deque<Stream> responses = new ArrayDeque<>();

  private Handler<Object> invalidMessageHandler = INVALID_MSG_HANDLER;
  private boolean close;
  private Promise<NetSocket> netSocketPromise;
  private int keepAliveTimeout;
  private int seq = 1;
  private long bytesRead;

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

  private HttpRequest createRequest(
    HttpMethod method,
    String uri,
    MultiMap headerMap,
    String authority,
    boolean chunked) {
    DefaultHttpRequest request = new DefaultHttpRequest(HttpUtils.toNettyHttpVersion(version), HttpMethodImpl.toNetty(method), uri, false);
    HttpHeaders headers = request.headers();
    if (headerMap != null) {
      for (Map.Entry<String, String> header : headerMap) {
        headers.add(header.getKey(), header.getValue());
      }
    }
    if (!headers.contains(HOST)) {
      request.headers().set(HOST, authority);
    } else {
      headers.remove(TRANSFER_ENCODING);
    }
    if (chunked) {
      HttpUtil.setTransferEncodingChunked(request, true);
    }
    if (options.isTryUseCompression() && request.headers().get(ACCEPT_ENCODING) == null) {
      // if compression should be used but nothing is specified by the user support deflate and gzip.
      request.headers().set(ACCEPT_ENCODING, DEFLATE_GZIP);
    }
    if (!options.isKeepAlive() && options.getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_1) {
      request.headers().set(CONNECTION, CLOSE);
    } else if (options.isKeepAlive() && options.getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_0) {
      request.headers().set(CONNECTION, KEEP_ALIVE);
    }
    return request;
  }

  private void beginRequest(Stream stream, HttpRequest request, Handler<AsyncResult<Void>> handler) {
    synchronized (this) {
      responses.add(stream);
      this.netSocketPromise = ((StreamImpl)stream).netSocketPromise;
      if (this.metrics != null) {
        stream.metric = this.metrics.requestBegin(this.endpointMetric, this.metric(), this.localAddress(), this.remoteAddress(), stream.request);
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null) {
        List<Map.Entry<String, String>> tags = new ArrayList<>();
        tags.add(new AbstractMap.SimpleEntry<>("http.url", stream.request.absoluteURI()));
        tags.add(new AbstractMap.SimpleEntry<>("http.method", stream.request.method().name()));
        BiConsumer<String, String> headers = (key, val) -> request.headers().add(key, val);
        stream.trace = tracer.sendRequest(stream.context, stream.request, stream.request.method.name(), headers, HttpUtils.CLIENT_REQUEST_TAG_EXTRACTOR);
      }
    }
    writeToChannel(request, handler == null ? null : context.promise(handler));
    if (request instanceof LastHttpContent) {
      endRequest(stream);
    }
  }

  private void endRequest(Stream s) {
    Stream next;
    boolean recycle;
    synchronized (this) {
      requests.pop();
      next = requests.peek();
      recycle = s.responseEnded;
      if (metrics != null) {
        metrics.requestEnd(s.metric);
      }
    }
    reportBytesWritten(bytesWritten);
    bytesWritten = 0L;
    if (next != null) {
      next.promise.complete((HttpClientStream) next);
    }
    if (recycle) {
      recycle();
    }
  }

  private void resetRequest(Stream stream) {
    boolean close;
    synchronized (this) {
      if (responses.remove(stream)) {
        // Already sent
        close = true;
      } else if (requests.remove(stream)) {
        // Not yet sent
        close = false;
      } else {
        // Response received
        return;
      }
    }
    if (close) {
      close();
    } else {
      recycle();
    }
  }

  private abstract static class Stream {

    protected final Promise<HttpClientStream> promise;
    protected final ContextInternal context;
    protected final int id;
    protected final HttpClientRequestImpl request;

    private Object trace;
    private Object metric;
    private HttpClientResponseImpl response;
    private boolean responseEnded;

    Stream(ContextInternal context, int id, HttpClientRequestImpl request) {
      this.context = context;
      this.id = id;
      this.request = request;
      this.promise = context.promise();
    }

    // Not really elegant... but well
    Object metric() {
      return metric;
    }

    abstract void handleContinue();
    abstract void handleBegin(HttpClientResponseImpl resp);
    abstract void handleChunk(Buffer buff);
    abstract void handleEnd(LastHttpContent trailer);
    abstract void handleWritabilityChanged(boolean writable);
    abstract void handleException(Throwable cause);
    abstract void handleClosed();

  }

  private void drainResponse(Stream n) {
    if (!n.responseEnded) {
      this.doResume();
    }
  }

  /**
   * We split the stream class in two classes so that the base {@link #Stream} class defines the (mutable)
   * state managed by the connection and this class defines the state managed by the stream implementation
   */
  private static class StreamImpl extends Stream implements HttpClientStream {

    private final Http1xClientConnection conn;
    private final InboundBuffer<Object> queue;
    private final Promise<NetSocket> netSocketPromise;
    private boolean reset;
    private boolean writable;

    StreamImpl(ContextInternal context, Http1xClientConnection conn, HttpClientRequestImpl request, Promise<NetSocket> netSocketPromise, int id) {
      super(context, id, request);

      this.writable = !conn.isNotWritable();
      this.conn = conn;
      this.netSocketPromise = netSocketPromise;
      this.queue = new InboundBuffer<>(context, 5)
        .drainHandler(v -> {
          EventLoop eventLoop = conn.context.nettyEventLoop();
          if (eventLoop.inEventLoop()) {
            drained();
          } else {
            eventLoop.execute(this::drained);
          }
        })
        .exceptionHandler(context::reportException);
    }

    private void drained() {
      conn.drainResponse(this);
    }

    @Override
    public int id() {
      return id;
    }

    @Override
    public Object metric() {
      return super.metric();
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
    public void writeHead(HttpMethod method, String uri, MultiMap headers, String authority, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, Handler<AsyncResult<Void>> handler) {
      HttpRequest request = conn.createRequest(method, uri, headers, authority, chunked);
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
      writeHead(request, handler == null ? null : context.promise(handler));
    }

    private void writeHead(HttpRequest request, Handler<AsyncResult<Void>> handler) {
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        conn.beginRequest(this, request, handler);
      } else {
        eventLoop.execute(() -> writeHead(request, handler));
      }
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
      writeBuffer(msg, handler == null ? null : context.promise(handler));
    }

    private void writeBuffer(HttpContent content, FutureListener<Void> listener) {
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        conn.writeToChannel(content, listener);
        if (content instanceof LastHttpContent) {
          conn.endRequest(this);
        }
      } else {
        eventLoop.execute(() -> writeBuffer(content, listener));
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
      synchronized (conn) {
        return !writable;
      }
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
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        reset();
      } else {
        eventLoop.execute(this::reset);
      }
    }

    private void reset() {
      conn.resetRequest(this);
    }

    @Override
    public StreamPriority priority() {
      return null;
    }

    @Override
    public void updatePriority(StreamPriority streamPriority) {
    }

    @Override
    void handleWritabilityChanged(boolean writable) {
      boolean drain;
      synchronized (conn) {
        drain = !this.writable && writable;
        this.writable = writable;
      }
      if (drain) {
        request.handleDrained();
      }
    }

    void handleContinue() {
      request.handleContinue();
    }

    void handleBegin(HttpClientResponseImpl response) {
      queue.handler(item -> {
        if (item instanceof MultiMap) {
          response.handleEnd((MultiMap) item);
        } else {
          response.handleChunk((Buffer) item);
        }
      });
      request.handleResponse(response);
    }

    void handleChunk(Buffer buff) {
      if (!queue.write(buff)) {
        conn.doPause();
      }
    }

    void handleEnd(LastHttpContent trailer) {
      queue.write(new HeadersAdaptor(trailer.trailingHeaders()));
    }

    void handleException(Throwable cause) {
      request.handleException(cause);
    }

    @Override
    void handleClosed() {
      handleException(CLOSED_EXCEPTION);
    }
  }

  private void checkLifecycle() {
    if (close) {
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
      invalidMessageHandler.handle(msg);
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
      Stream stream;
      synchronized (this) {
        stream = responses.getFirst();
      }
      stream.context.schedule(null, v -> stream.handleContinue());
    } else {
      Stream stream;
      HttpClientResponseImpl response;
      HttpClientRequestImpl request;
      synchronized (this) {
        stream = responses.getFirst();
        request = stream.request;

        HttpVersion version;
        if (resp.protocolVersion() == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
          version = io.vertx.core.http.HttpVersion.HTTP_1_0;
        } else {
          version = io.vertx.core.http.HttpVersion.HTTP_1_1;
        }
        response = new HttpClientResponseImpl(request, version, (HttpClientStream) stream, resp.status().code(), resp.status().reasonPhrase(), new HeadersAdaptor(resp.headers()));
        stream.response = response;

        if (metrics != null) {
          metrics.responseBegin(stream.metric, response);
        }

        //
        if (resp.status().code() != 100 && request.method() != io.vertx.core.http.HttpMethod.CONNECT) {
          // See https://tools.ietf.org/html/rfc7230#section-6.3
          String responseConnectionHeader = resp.headers().get(HttpHeaderNames.CONNECTION);
          io.netty.handler.codec.http.HttpVersion protocolVersion = resp.protocolVersion();
          String requestConnectionHeader = request.headers().get(HttpHeaderNames.CONNECTION);
          // We don't need to protect against concurrent changes on forceClose as it only goes from false -> true
          if (HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(responseConnectionHeader) || HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(requestConnectionHeader)) {
            // In all cases, if we have a close connection option then we SHOULD NOT treat the connection as persistent
            this.close = true;
          } else if (protocolVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0 && !HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(responseConnectionHeader)) {
            // In the HTTP/1.0 case both request/response need a keep-alive connection header the connection to be persistent
            // currently Vertx forces the Connection header if keepalive is enabled for 1.0
            this.close = true;
          }
          String keepAliveHeader = resp.headers().get(HttpHeaderNames.KEEP_ALIVE);
          if (keepAliveHeader != null) {
            int timeout = HttpUtils.parseKeepAliveHeaderTimeout(keepAliveHeader);
            if (timeout != -1) {
              this.keepAliveTimeout = timeout;
            }
          }
        }
      }

      //
      stream.context.schedule(response, stream::handleBegin);

      Promise<NetSocket> promise = netSocketPromise;
      netSocketPromise = null;
      if (promise != null) {
        if ((request.method == HttpMethod.CONNECT &&
             response.statusCode() == 200) || (
             request.method == HttpMethod.GET &&
             request.headers().contains("connection", "Upgrade", false) &&
             response.statusCode() == 101)) {
          // remove connection from the pool
          listener.onEvict();

          // remove old http handlers
          ChannelPipeline pipeline = chctx.pipeline();
          ChannelHandler inflater = pipeline.get(HttpContentDecompressor.class);
          if (inflater != null) {
            pipeline.remove(inflater);
          }

          // removing this codec might fire pending buffers in the HTTP decoder
          // this happens when the channel reads the HTTP response and the following data in a single buffer
          Deque<Object> pending = new ArrayDeque<>();
          invalidMessageHandler = pending::add;
          pipeline.remove("codec");

          // replace the old handler with one that handle plain sockets
          NetSocketImpl socket = new NetSocketImpl(vertx, chctx, context, client.getSslHelper(), metrics) {
            @Override
            protected void handleClosed() {
              if (metrics != null) {
                metrics.responseEnd(stream.metric, response);
              }
              super.handleClosed();
            }
          };
          socket.metric(metric());
          pipeline.replace("handler", "handler", VertxHandler.create(ctx -> socket));

          // Handle back response
          promise.complete(socket);

          // Redeliver pending messages
          for (Object msg : pending) {
            pipeline.fireChannelRead(msg);
          }
        } else {
          promise.fail("Server responded with " + response.statusCode() + " code instead of 200");
        }
      }
    }
  }

  private void handleResponseChunk(Buffer buff) {
    Stream resp;
    synchronized (this) {
      resp = responses.getFirst();
      bytesRead += buff.length();
    }
    if (resp != null) {
      resp.context.schedule(buff, resp::handleChunk);
    }
  }

  private void handleResponseEnd(LastHttpContent trailer) {
    Stream stream;
    boolean check;
    long bytesRead;
    synchronized (this) {
      stream = responses.getFirst();
      if (stream.response == null) {
        // 100-continue
        return;
      }
      responses.pop();
      bytesRead = this.bytesRead;
      this.bytesRead = 0L;
      close |= !options.isKeepAlive();
      stream.responseEnded = true;
      check = requests.peek() != stream;
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      tracer.receiveResponse(stream.context, stream.response, stream.trace, null, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.response);
    }
    stream.context.schedule(trailer, stream::handleEnd);
    this.doResume();
    reportBytesRead(bytesRead);
    if (check) {
      checkLifecycle();
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
      ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = initializeWebSocketExtensionHandshakers(client.getOptions());
      if (!extensionHandshakers.isEmpty()) {
        p.addBefore("handler", "webSocketsExtensionsHandler", new WebSocketClientExtensionHandler(
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
            options.getMaxWebSocketFrameSize(),
            options.getMaxWebSocketMessageSize());
          w.subProtocol(handshaker.actualSubprotocol());
          return w;
        });
        if (ar.failed()) {
          close();
        } else {
          webSocket = (WebSocketImpl) wsRes.result();
          webSocket.registerHandler(vertx.eventBus());

        }
        log.debug("WebSocket handshake complete");
        if (metrics != null) {
          webSocket.setMetric(metrics.connected(endpointMetric, metric(), webSocket));
        }
        getContext().dispatch(wsRes, res -> {
          if (res.succeeded()) {
            webSocket.headers(ar.result());
          }
          wsHandler.handle(res);
          if (res.succeeded()) {
            webSocket.headers(null);
          }
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

  ArrayList<WebSocketClientExtensionHandshaker> initializeWebSocketExtensionHandshakers(HttpClientOptions options) {
    ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = new ArrayList<WebSocketClientExtensionHandshaker>();
    if (options.getTryWebSocketDeflateFrameCompression()) {
      extensionHandshakers.add(new DeflateFrameClientExtensionHandshaker(options.getWebSocketCompressionLevel(),
        false));
    }

    if (options.getTryUsePerMessageWebSocketCompression()) {
      extensionHandshakers.add(new PerMessageDeflateClientExtensionHandshaker(options.getWebSocketCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        options.getWebSocketCompressionAllowClientNoContext(), options.getWebSocketCompressionRequestServerNoContext()));
    }

    return extensionHandshakers;
  }

  @Override
  public  void handleInterestedOpsChanged() {
    boolean writable = !isNotWritable();
    ContextInternal context;
    Handler<Boolean> handler;
    synchronized (this) {
      Stream current = requests.peek();
      if (current != null) {
        context = current.context;
        handler = current::handleWritabilityChanged;
      } else if (webSocket != null) {
        context = webSocket.context;
        handler = webSocket::handleWritabilityChanged;
      } else {
        return;
      }
    }
    context.schedule(writable, handler);
  }

  /**
   * @return a list of all pending streams
   */
  private Iterable<Stream> pendingStreams() {
    // There might be duplicate between the requets list and the responses list
    LinkedHashSet<Stream> list = new LinkedHashSet<>();
    list.addAll(requests);
    list.addAll(responses);
    return list;
  }

  protected void handleClosed() {
    super.handleClosed();
    if (metrics != null) {
      metrics.endpointDisconnected(endpointMetric, metric());
    }
    WebSocketImpl ws;
    VertxTracer tracer = context.tracer();
    Iterable<Stream> streams;
    synchronized (this) {
      ws = webSocket;
      streams = pendingStreams();
    }
    if (netSocketPromise != null) {
      netSocketPromise.fail(ConnectionBase.CLOSED_EXCEPTION);
    }
    if (ws != null) {
      ws.handleClosed();
    }
    for (Stream stream : streams) {
      if (metrics != null) {
        metrics.requestReset(stream.metric);
      }
      if (tracer != null) {
        tracer.receiveResponse(stream.context, null, stream.trace, ConnectionBase.CLOSED_EXCEPTION, TagExtractor.empty());
      }
      stream.context.schedule(null, v -> stream.handleClosed());
    }
  }

  protected void handleIdle() {
    synchronized (this) {
      if (webSocket == null && responses.isEmpty() && requests.isEmpty()) {
        return;
      }
    }
    super.handleIdle();
  }

  @Override
  protected void handleException(Throwable e) {
    super.handleException(e);
    WebSocketImpl ws;
    Iterable<Stream> streams;
    synchronized (this) {
      ws = webSocket;
      streams = pendingStreams();
    }
    if (ws != null) {
      ws.handleException(e);
    }
    for (Stream stream : streams) {
      stream.handleException(e);
    }
  }

  @Override
  public void createStream(ContextInternal context, HttpClientRequestImpl req, Promise<NetSocket> netSocketPromise, Handler<AsyncResult<HttpClientStream>> handler) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      StreamImpl stream;
      synchronized (this) {
        stream = new StreamImpl(context, this, req, netSocketPromise, seq++);
        if (requests.isEmpty()) {
          stream.promise.complete(stream);
        }
        requests.add(stream);
      }
      stream.promise.future().setHandler(handler);
    } else {
      eventLoop.execute(() -> {
        createStream(context, req, netSocketPromise, handler);
      });
    }
  }

  private void recycle() {
    long expiration = keepAliveTimeout == 0 ? 0L : System.currentTimeMillis() + keepAliveTimeout * 1000;
    listener.onRecycle(expiration);
  }
}
