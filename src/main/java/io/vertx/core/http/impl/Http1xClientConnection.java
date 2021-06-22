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
import io.netty.util.concurrent.FutureListener;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.NetSocketInternal;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
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
public class Http1xClientConnection extends Http1xConnectionBase<WebSocketImpl> implements HttpClientConnection {

  private static final Logger log = LoggerFactory.getLogger(Http1xClientConnection.class);

  private static final Handler<Object> INVALID_MSG_HANDLER = msg -> {
    ReferenceCountUtil.release(msg);
    throw new IllegalStateException("Invalid object " + msg);
  };

  private final HttpClientImpl client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final SocketAddress server;
  public final ClientMetrics metrics;
  private final HttpVersion version;
  private final long lowWaterMark;
  private final long highWaterMark;

  private Deque<Stream> requests = new ArrayDeque<>();
  private Deque<Stream> responses = new ArrayDeque<>();
  private boolean closed;
  private boolean evicted;

  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Object> invalidMessageHandler = INVALID_MSG_HANDLER;
  private boolean close;
  private boolean shutdown;
  private long shutdownTimerID = -1L;
  private boolean isConnect;
  private int keepAliveTimeout;
  private long expirationTimestamp;
  private int seq = 1;
  private long bytesWindow;

  private long lastResponseReceivedTimestamp;

  Http1xClientConnection(HttpVersion version,
                         HttpClientImpl client,
                         ChannelHandlerContext channel,
                         boolean ssl,
                         SocketAddress server,
                         ContextInternal context,
                         ClientMetrics metrics) {
    super(context, channel);
    this.client = client;
    this.options = client.getOptions();
    this.ssl = ssl;
    this.server = server;
    this.metrics = metrics;
    this.version = version;
    this.bytesWindow = 0;
    this.highWaterMark = channel.channel().config().getWriteBufferHighWaterMark();
    this.lowWaterMark = channel.channel().config().getWriteBufferLowWaterMark();
    this.keepAliveTimeout = options.getKeepAliveTimeout();
    this.expirationTimestamp = expirationTimestampOf(keepAliveTimeout);
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    // Never changes
    return this;
  }

  @Override
  public long concurrency() {
    return options.isPipelining() ? options.getPipeliningLimit() : 1;
  }

  /**
   * @return a raw {@code NetSocket} - for internal use
   */
  public NetSocketInternal toNetSocket() {
    removeChannelHandlers();
    NetSocketImpl socket = new NetSocketImpl(context, chctx, null, metrics());
    socket.metric(metric());
    evictionHandler.handle(null);
    chctx.pipeline().replace("handler", "handler", VertxHandler.create(ctx -> socket));
    return socket;
  }

  private HttpRequest createRequest(
    HttpMethod method,
    String uri,
    MultiMap headerMap,
    String authority,
    boolean chunked,
    ByteBuf buf,
    boolean end) {
    HttpRequest request = new DefaultHttpRequest(HttpUtils.toNettyHttpVersion(version), method.toNetty(), uri, false);
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
    if (end) {
      if (buf != null) {
        request = new AssembledFullHttpRequest(request, buf);
      } else {
        request = new AssembledFullHttpRequest(request);
      }
    } else if (buf != null) {
      request = new AssembledHttpRequest(request, buf);
    }
    return request;
  }

  private void beginRequest(Stream stream, HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, boolean connect, Handler<AsyncResult<Void>> handler) {
    request.id = stream.id;
    request.remoteAddress = remoteAddress();
    stream.bytesWritten += buf != null ? buf.readableBytes() : 0L;
    HttpRequest nettyRequest = createRequest(request.method, request.uri, request.headers, request.authority, chunked, buf, end);
    synchronized (this) {
      responses.add(stream);
      this.isConnect = connect;
      if (this.metrics != null) {
        stream.metric = this.metrics.requestBegin(request.uri, request);
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null) {
        BiConsumer<String, String> headers = (key, val) -> nettyRequest.headers().add(key, val);
        stream.trace = tracer.sendRequest(stream.context, SpanKind.RPC, options.getTracingPolicy(), request, request.method.name(), headers, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
      }
    }
    writeToChannel(nettyRequest, handler == null ? null : context.promise(handler));
    if (end) {
      endRequest(stream);
    }
  }

  private void writeBuffer(Stream s, ByteBuf buff, boolean end, FutureListener<Void> listener) {
    s.bytesWritten += buff != null ? buff.readableBytes() : 0L;
    Object msg;
    if (isConnect) {
      msg = buff != null ? buff : Unpooled.EMPTY_BUFFER;
      if (end) {
        writeToChannel(msg, channelFuture()
          .addListener(listener)
          .addListener(v -> close())
        );
      } else {
        writeToChannel(msg);
      }
    } else {
      if (end) {
        if (buff != null && buff.isReadable()) {
          msg = new DefaultLastHttpContent(buff, false);
        } else {
          msg = LastHttpContent.EMPTY_LAST_CONTENT;
        }
      } else {
        msg = new DefaultHttpContent(buff);
      }
      writeToChannel(msg, listener);
      if (end) {
        endRequest(s);
      }
    }
  }

  private void endRequest(Stream s) {
    Stream next;
    boolean checkLifecycle;
    synchronized (this) {
      requests.pop();
      next = requests.peek();
      checkLifecycle = s.responseEnded;
      if (metrics != null) {
        metrics.requestEnd(s.metric, s.bytesWritten);
      }
    }
    flushBytesWritten();
    if (next != null) {
      next.promise.complete((HttpClientStream) next);
    }
    if (checkLifecycle) {
      checkLifecycle();
    }
  }

  /**
   * Resets the given {@code stream}.
   *
   * @param stream to reset
   * @return whether the stream should be considered as closed
   */
  private boolean reset(Stream stream) {
    boolean isInflight;
    synchronized (this) {
      isInflight = responses.remove(stream) || (requests.remove(stream) && stream.responseEnded);
      close = isInflight;
    }
    checkLifecycle();
    return !isInflight;
  }

  private void receiveBytes(int len) {
    boolean le = bytesWindow <= highWaterMark;
    bytesWindow += len;
    boolean gt = bytesWindow > highWaterMark;
    if (le && gt) {
      doPause();
    }
  }

  private void ackBytes(int len) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      boolean gt = bytesWindow > lowWaterMark;
      bytesWindow -= len;
      boolean le = bytesWindow <= lowWaterMark;
      if (gt && le) {
        doResume();
      }
    } else {
      eventLoop.execute(() -> ackBytes(len));
    }
  }

  private abstract static class Stream {

    protected final Promise<HttpClientStream> promise;
    protected final ContextInternal context;
    protected final int id;

    private Object trace;
    private Object metric;
    private HttpResponseHead response;
    private boolean responseEnded;
    private long bytesRead;
    private long bytesWritten;

    Stream(ContextInternal context, int id) {
      this.context = context;
      this.id = id;
      this.promise = context.promise();
    }

    // Not really elegant... but well
    Object metric() {
      return metric;
    }

    abstract void handleContinue();
    abstract void handleHead(HttpResponseHead response);
    abstract void handleChunk(Buffer buff);
    abstract void handleEnd(LastHttpContent trailer);
    abstract void handleWritabilityChanged(boolean writable);
    abstract void handleException(Throwable cause);
    abstract void handleClosed();

  }

  /**
   * We split the stream class in two classes so that the base {@link #Stream} class defines the (mutable)
   * state managed by the connection and this class defines the state managed by the stream implementation
   */
  private static class StreamImpl extends Stream implements HttpClientStream {

    private final Http1xClientConnection conn;
    private final InboundBuffer<Object> queue;
    private boolean reset;
    private boolean writable;
    private HttpRequestHead request;
    private Handler<HttpResponseHead> headHandler;
    private Handler<Buffer> chunkHandler;
    private Handler<MultiMap> endHandler;
    private Handler<Void> drainHandler;
    private Handler<Void> continueHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> closeHandler;

    StreamImpl(ContextInternal context, Http1xClientConnection conn, int id) {
      super(context, id);

      this.writable = !conn.isNotWritable();
      this.conn = conn;
      this.queue = new InboundBuffer<>(context, 5)
        .handler(item -> {
          if (item instanceof MultiMap) {
            Handler<MultiMap> handler = endHandler;
            if (handler != null) {
              handler.handle((MultiMap) item);
            }
          } else {
            Buffer buffer = (Buffer) item;
            int len = buffer.length();
            conn.ackBytes(len);
            Handler<Buffer> handler = chunkHandler;
            if (handler != null) {
              handler.handle(buffer);
            }
          }
        })
        .exceptionHandler(context::reportException);
    }

    @Override
    public void continueHandler(Handler<Void> handler) {
      continueHandler = handler;
    }

    @Override
    public void drainHandler(Handler<Void> handler) {
      drainHandler = handler;
    }

    @Override
    public void exceptionHandler(Handler<Throwable> handler) {
      exceptionHandler = handler;
    }

    @Override
    public void headHandler(Handler<HttpResponseHead> handler) {
      this.headHandler = handler;
    }

    @Override
    public void closeHandler(Handler<Void> handler) {
      closeHandler = handler;
    }

    @Override
    public void priorityHandler(Handler<StreamPriority> handler) {
      // No op
    }

    @Override
    public void pushHandler(Handler<HttpClientPush> handler) {
      // No op
    }

    @Override
    public void unknownFrameHandler(Handler<HttpFrame> handler) {
      // No op
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
    public void writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect, Handler<AsyncResult<Void>> handler) {
      writeHead(request, chunked, buf, end, connect, handler == null ? null : context.promise(handler));
    }

    private void writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, boolean connect, Handler<AsyncResult<Void>> handler) {
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        this.request = request;
        conn.beginRequest(this, request, chunked, buf, end, connect, handler);
      } else {
        eventLoop.execute(() -> writeHead(request, chunked, buf, end, connect, handler));
      }
    }

    @Override
    public void writeBuffer(ByteBuf buff, boolean end, Handler<AsyncResult<Void>> handler) {
      if (buff != null || end) {
        FutureListener<Void> listener = handler == null ? null : context.promise(handler);
        writeBuffer(buff, end, listener);
      }
    }

    private void writeBuffer(ByteBuf buff, boolean end, FutureListener<Void> listener) {
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        conn.writeBuffer(this, buff, end, listener);
      } else {
        eventLoop.execute(() -> writeBuffer(buff, end, listener));
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
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        _reset(cause);
      } else {
        eventLoop.execute(() -> _reset(cause));
      }
    }

    private void _reset(Throwable cause) {
      boolean removed = conn.reset(this);
      context.execute(cause, this::handleException);
      if (removed) {
        context.execute(this::handleClosed);
      }
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
      Handler<Void> handler;
      boolean drain;
      synchronized (conn) {
        drain = !this.writable && writable;
        this.writable = writable;
        handler = drainHandler;
      }
      if (drain && handler != null) {
        handler.handle(null);
      }
    }

    void handleContinue() {
      if (continueHandler != null) {
        continueHandler.handle(null);
      }
    }

    @Override
    void handleHead(HttpResponseHead response) {
      Handler<HttpResponseHead> handler = headHandler;
      if (handler != null) {
        context.emit(response, handler);
      }
    }

    @Override
    public void chunkHandler(Handler<Buffer> handler) {
      chunkHandler = handler;
    }

    @Override
    public void endHandler(Handler<MultiMap> handler) {
      endHandler = handler;
    }

    void handleChunk(Buffer buff) {
      queue.write(buff);
    }

    void handleEnd(LastHttpContent trailer) {
      queue.write(new HeadersAdaptor(trailer.trailingHeaders()));
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
    }

    void handleException(Throwable cause) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(cause);
      }
    }

    @Override
    void handleClosed() {
      handleException(CLOSED_EXCEPTION);
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
    }
  }

  private void checkLifecycle() {
    if (close || (shutdown && requests.isEmpty() && responses.isEmpty())) {
      close();
    } else if (!isConnect) {
      expirationTimestamp = expirationTimestampOf(keepAliveTimeout);
    }
  }

  @Override
  public Future<Void> close() {
    if (!evicted) {
      evicted = true;
      if (evictionHandler != null) {
        evictionHandler.handle(null);
      }
    }
    return super.close();
  }

  private Throwable validateMessage(Object msg) {
    if (msg instanceof HttpObject) {
      HttpObject obj = (HttpObject) msg;
      DecoderResult result = obj.decoderResult();
      if (result.isFailure()) {
        return result.cause();
      } else if (obj instanceof io.netty.handler.codec.http.HttpResponse) {
        io.netty.handler.codec.http.HttpVersion version = ((io.netty.handler.codec.http.HttpResponse) obj).protocolVersion();
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
      ReferenceCountUtil.release(msg);
      fail(error);
    } else if (msg instanceof HttpObject) {
      handleHttpMessage((HttpObject) msg);
    } else if (msg instanceof ByteBuf && isConnect) {
      handleChunk((ByteBuf) msg);
    } else if (msg instanceof WebSocketFrame) {
      handleWsFrame((WebSocketFrame) msg);
    } else {
      invalidMessageHandler.handle(msg);
    }
  }

  private void handleHttpMessage(HttpObject obj) {
    Stream stream;
    synchronized (this) {
      stream = responses.peekFirst();
    }
    if (stream == null) {
      fail(new VertxException("Received HTTP message with no request in progress"));
    } else if (obj instanceof io.netty.handler.codec.http.HttpResponse) {
      io.netty.handler.codec.http.HttpResponse response = (io.netty.handler.codec.http.HttpResponse) obj;
      HttpVersion version;
      if (response.protocolVersion() == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = io.vertx.core.http.HttpVersion.HTTP_1_0;
      } else {
        version = io.vertx.core.http.HttpVersion.HTTP_1_1;
      }
      handleResponseBegin(stream, new HttpResponseHead(
        version,
        response.status().code(),
        response.status().reasonPhrase(),
        new HeadersAdaptor(response.headers())));
    } else if (obj instanceof HttpContent) {
      HttpContent chunk = (HttpContent) obj;
      if (chunk.content().isReadable()) {
        handleResponseChunk(stream, chunk.content());
      }
      if (!isConnect && chunk instanceof LastHttpContent) {
        handleResponseEnd(stream, (LastHttpContent) chunk);
      }
    }
  }

  private void handleChunk(ByteBuf chunk) {
    Stream stream;
    synchronized (this) {
      stream = responses.peekFirst();
      if (stream == null) {
        return;
      }
    }
    if (chunk.isReadable()) {
      handleResponseChunk(stream, chunk);
    }
  }

  private void handleResponseBegin(Stream stream, HttpResponseHead response) {
    if (response.statusCode == 100) {
      stream.context.execute(null, v -> stream.handleContinue());
    } else {
      HttpRequestHead request;
      synchronized (this) {
        request = ((StreamImpl)stream).request;
        stream.response = response;

        if (metrics != null) {
          metrics.responseBegin(stream.metric, response);
        }

        //
        if (response.statusCode != 100 && request.method != HttpMethod.CONNECT) {
          // See https://tools.ietf.org/html/rfc7230#section-6.3
          String responseConnectionHeader = response.headers.get(HttpHeaderNames.CONNECTION);
          String requestConnectionHeader = request.headers != null ? request.headers.get(HttpHeaderNames.CONNECTION) : null;
          // We don't need to protect against concurrent changes on forceClose as it only goes from false -> true
          if (HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(responseConnectionHeader) || HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(requestConnectionHeader)) {
            // In all cases, if we have a close connection option then we SHOULD NOT treat the connection as persistent
            this.close = true;
          } else if (response.version == HttpVersion.HTTP_1_0 && !HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(responseConnectionHeader)) {
            // In the HTTP/1.0 case both request/response need a keep-alive connection header the connection to be persistent
            // currently Vertx forces the Connection header if keepalive is enabled for 1.0
            this.close = true;
          }
          String keepAliveHeader = response.headers.get(HttpHeaderNames.KEEP_ALIVE);
          if (keepAliveHeader != null) {
            int timeout = HttpUtils.parseKeepAliveHeaderTimeout(keepAliveHeader);
            if (timeout != -1) {
              this.keepAliveTimeout = timeout;
            }
          }
        }
      }

      //
      stream.handleHead(response);

      if (isConnect) {
        if ((request.method == HttpMethod.CONNECT &&
             response.statusCode == 200) || (
             request.method == HttpMethod.GET &&
             request.headers.contains("connection", "Upgrade", false) &&
             response.statusCode == 101)) {
          removeChannelHandlers();
        }
      }
    }
  }

  /**
   * Remove all HTTP channel handlers of this connection
   *
   * @return the messages emitted by the removed handlers during their removal
   */
  private void removeChannelHandlers() {
    ChannelPipeline pipeline = chctx.pipeline();
    ChannelHandler inflater = pipeline.get(HttpContentDecompressor.class);
    if (inflater != null) {
      pipeline.remove(inflater);
    }
    // removing this codec might fire pending buffers in the HTTP decoder
    // this happens when the channel reads the HTTP response and the following data in a single buffer
    Handler<Object> prev = invalidMessageHandler;
    invalidMessageHandler = msg -> {
      ReferenceCountUtil.release(msg);
    };
    try {
      pipeline.remove("codec");
    } finally {
      invalidMessageHandler = prev;
    }
  }

  private void handleResponseChunk(Stream stream, ByteBuf chunk) {
    Buffer buff = Buffer.buffer(VertxHandler.safeBuffer(chunk));
    int len = buff.length();
    receiveBytes(len);
    stream.bytesRead += len;
    stream.context.execute(buff, stream::handleChunk);
  }

  private void handleResponseEnd(Stream stream, LastHttpContent trailer) {
    boolean check;
    synchronized (this) {
      if (stream.response == null) {
        // 100-continue
        return;
      }
      responses.pop();
      close |= !options.isKeepAlive();
      stream.responseEnded = true;
      check = requests.peek() != stream;
    }
    VertxTracer tracer = context.tracer();
    if (tracer != null) {
      tracer.receiveResponse(stream.context, stream.response, stream.trace, null, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.bytesRead);
    }
    flushBytesRead();
    if (check) {
      checkLifecycle();
    }
    lastResponseReceivedTimestamp = System.currentTimeMillis();
    stream.context.execute(trailer, stream::handleEnd);
  }

  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  synchronized void toWebSocket(
    ContextInternal context,
    String requestURI,
    MultiMap headers,
    WebsocketVersion vers,
    List<String> subProtocols,
    int maxWebSocketFrameSize,
    Promise<WebSocket> promise) {
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
          extensionHandshakers.toArray(new WebSocketClientExtensionHandshaker[0])));
      }

      String subp = null;
      if (subProtocols != null) {
        subp = String.join(",", subProtocols);
      }
      WebSocketClientHandshaker handshaker = WebSocketHandshakeInboundHandler.newHandshaker(
        wsuri,
        version,
        subp,
        !extensionHandshakers.isEmpty(),
        nettyHeaders,
        maxWebSocketFrameSize,
        !options.isSendUnmaskedFrames());

      WebSocketHandshakeInboundHandler handshakeInboundHandler = new WebSocketHandshakeInboundHandler(handshaker, ar -> {
        AsyncResult<WebSocket> wsRes = ar.map(v -> {
          WebSocketImpl w = new WebSocketImpl(
            context,
            Http1xClientConnection.this,
            version != WebSocketVersion.V00,
            options.getWebSocketClosingTimeout(),
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
          log.debug("WebSocket handshake complete");
          HttpClientMetrics metrics = client.metrics();
          if (metrics != null) {
            webSocket.setMetric(metrics.connected(webSocket));
          }
        }
        getContext().emit(wsRes, res -> {
          if (res.succeeded()) {
            webSocket.headers(ar.result());
          }
          promise.handle(res);
          if (res.succeeded()) {
            webSocket.headers(null);
          }
        });
      });
      p.addBefore("handler", "handshakeCompleter", handshakeInboundHandler);
      handshaker.handshake(chctx.channel());
    } catch (Exception e) {
      handleException(e);
    }
  }

  ArrayList<WebSocketClientExtensionHandshaker> initializeWebSocketExtensionHandshakers(HttpClientOptions options) {
    ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = new ArrayList<>();
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
    context.execute(writable, handler);
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
    long timerID = shutdownTimerID;
    if (timerID != -1) {
      shutdownTimerID = -1L;
      vertx.cancelTimer(timerID);
    }
    closed = true;
    if (metrics != null) {
      HttpClientMetrics met = client.metrics();
      met.endpointDisconnected(metrics);
    }
    if (!evicted) {
      evicted = true;
      if (evictionHandler != null) {
        evictionHandler.handle(null);
      }
    }
    WebSocketImpl ws;
    VertxTracer tracer = context.tracer();
    Iterable<Stream> streams;
    synchronized (this) {
      ws = webSocket;
      streams = pendingStreams();
    }
    if (ws != null) {
      ws.handleConnectionClosed();
    }
    for (Stream stream : streams) {
      if (metrics != null) {
        metrics.requestReset(stream.metric);
      }
      Object trace = stream.trace;
      if (tracer != null && trace != null) {
        tracer.receiveResponse(stream.context, null, trace, ConnectionBase.CLOSED_EXCEPTION, TagExtractor.empty());
      }
      stream.context.execute(null, v -> stream.handleClosed());
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
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      StreamImpl stream;
      synchronized (this) {
        if (closed) {
          stream = null;
        } else {
          stream = new StreamImpl(context, this, seq++);
          requests.add(stream);
          if (requests.size() == 1) {
            stream.promise.complete(stream);
          }
        }
      }
      if (stream != null) {
        stream.promise.future().onComplete(handler);
      } else {
        handler.handle(Future.failedFuture(CLOSED_EXCEPTION));
      }
    } else {
      eventLoop.execute(() -> {
        createStream(context, handler);
      });
    }
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return lastResponseReceivedTimestamp;
  }

  @Override
  public boolean isValid() {
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
  }

  @Override
  public void shutdown(long timeout, Handler<AsyncResult<Void>> handler) {
    shutdown(timeout, vertx.promise(handler));
  }

  @Override
  public Future<Void> shutdown(long timeoutMs) {
    PromiseInternal<Void> promise = vertx.promise();
    shutdown(timeoutMs, promise);
    return promise.future();
  }

  private synchronized void shutdownNow() {
    shutdownTimerID = -1L;
    close();
  }

  private void shutdown(long timeoutMs, PromiseInternal<Void> promise) {
    synchronized (this) {
      if (shutdown) {
        promise.fail("Already shutdown");
        return;
      }
      shutdown = true;
      closeFuture().onComplete(promise);
    }
    synchronized (this) {
      if (!closed) {
        if (timeoutMs > 0L) {
          shutdownTimerID = context.setTimer(timeoutMs, id -> shutdownNow());
        } else {
          close = true;
        }
      }
    }
    checkLifecycle();
  }

  /**
   * Compute the expiration timeout of the connection, relative to the current time.
   *
   * @param timeout the timeout
   * @return the expiration timestamp
   */
  private static long expirationTimestampOf(long timeout) {
    return timeout == 0 ? 0L : System.currentTimeMillis() + timeout * 1000;
  }
}
