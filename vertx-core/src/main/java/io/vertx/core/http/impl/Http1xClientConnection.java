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
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.Zstd;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.WebSocketVersion;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.http.impl.http2.Http2ClientPush;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.PromiseInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.internal.concurrent.InboundMessageQueue;
import io.vertx.core.internal.net.NetSocketInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.MessageWrite;
import io.vertx.core.net.impl.NetSocketImpl;
import io.vertx.core.net.impl.VertxHandler;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;

import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http.websocketx.WebSocketVersion.*;
import static io.vertx.core.http.HttpHeaders.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Http1xClientConnection extends Http1xConnection implements HttpClientConnection {

  private static final Handler<Object> INVALID_MSG_HANDLER = ReferenceCountUtil::release;

  private final HttpClientBase client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final SocketAddress server;
  private final HostAndPort authority;
  public final ClientMetrics metrics;
  private final HttpVersion version;
  private final boolean pooled;
  private final long lifetimeEvictionTimestamp;

  private final Deque<Stream> requests = new ArrayDeque<>();
  private final Deque<Stream> responses = new ArrayDeque<>();
  private boolean closed;
  private boolean evicted;

  private Handler<Void> evictionHandler = DEFAULT_EVICTION_HANDLER;
  private Handler<Object> invalidMessageHandler = INVALID_MSG_HANDLER;
  private boolean wantClose;
  private boolean isConnect;
  private int keepAliveTimeout;
  private long expirationTimestamp;
  private int seq = 1;
  private Deque<WebSocketFrame> pendingFrames;

  private long lastResponseReceivedTimestamp;

  Http1xClientConnection(HttpVersion version,
                         HttpClientBase client,
                         ChannelHandlerContext chctx,
                         boolean ssl,
                         SocketAddress server,
                         HostAndPort authority,
                         ContextInternal context,
                         ClientMetrics metrics,
                         boolean pooled,
                         long maxLifetime) {
    super(context, chctx);
    this.client = client;
    this.options = client.options();
    this.ssl = ssl;
    this.server = server;
    this.authority = authority;
    this.metrics = metrics;
    this.version = version;
    this.lifetimeEvictionTimestamp = maxLifetime > 0 ? System.currentTimeMillis() + maxLifetime : Long.MAX_VALUE;
    this.keepAliveTimeout = options.getKeepAliveTimeout();
    this.expirationTimestamp = expirationTimestampOf(keepAliveTimeout);
    this.pooled = pooled;
  }

  @Override
  public HostAndPort authority() {
    return authority;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    evictionHandler = handler;
    return this;
  }

  @Override
  public HttpClientConnection invalidMessageHandler(Handler<Object> handler) {
    invalidMessageHandler = handler;
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

  @Override
  public synchronized long activeStreams() {
    return requests.isEmpty() && responses.isEmpty() ? 0 : 1;
  }

  @Override
  public boolean pooled() {
    return pooled;
  }

  /**
   * @return a raw {@code NetSocket} - for internal use - must be called from event-loop
   */
  public NetSocketInternal toNetSocket() {
    evictionHandler.handle(null);
    chctx.pipeline().replace("handler", "handler", VertxHandler.create(ctx -> {
      NetSocketImpl socket = new NetSocketImpl(context, ctx, null, null, metrics(), false);
      socket.metric(metric());
      return socket;
    }));
    VertxHandler<NetSocketImpl> handler = (VertxHandler<NetSocketImpl>) chctx.pipeline().get(VertxHandler.class);
    return handler.getConnection();
  }

  private HttpRequest createRequest(
    HttpMethod method,
    String uri,
    MultiMap headerMap,
    HostAndPort authority,
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
      request.headers().set(HOST, authority.toString(ssl));
    } else {
      headers.remove(TRANSFER_ENCODING);
    }
    if (chunked) {
      HttpUtil.setTransferEncodingChunked(request, true);
    }
    if (options.isDecompressionSupported() && request.headers().get(ACCEPT_ENCODING) == null) {
      // if compression should be used but nothing is specified by the user support deflate and gzip.
      CharSequence acceptEncoding = determineCompressionAcceptEncoding();
      request.headers().set(ACCEPT_ENCODING, acceptEncoding);
    }
    if (!options.isKeepAlive() && options.getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_1) {
      request.headers().set(CONNECTION, CLOSE);
    } else if (options.isKeepAlive() && options.getProtocolVersion() == io.vertx.core.http.HttpVersion.HTTP_1_0) {
      request.headers().set(CONNECTION, KEEP_ALIVE);
    }
    if (end) {
      if (buf != null) {
        request = new VertxFullHttpRequest(request, buf);
      } else {
        request = new VertxFullHttpRequest(request);
      }
    } else if (buf != null) {
      request = new VertxAssembledHttpRequest(request, buf);
    }
    return request;
  }

  public static CharSequence determineCompressionAcceptEncoding() {
    if (isBrotliAvailable() && isZstdAvailable()) {
      return DEFLATE_GZIP_ZSTD_BR_SNAPPY;
    }
    else if (!isBrotliAvailable() && isZstdAvailable()) {
      return DEFLATE_GZIP_ZSTD;
    }
    else if (isBrotliAvailable() && !isZstdAvailable()) {
      return DEFLATE_GZIP_BR;
    } else {
      return DEFLATE_GZIP;
    }
  }

  // Encapsulated in a method, so GraalVM can substitute it
  private static boolean isBrotliAvailable() {
    return Brotli.isAvailable();
  }

  // Encapsulated in a method, so GraalVM can substitute it
  private static boolean isZstdAvailable() {
    return Zstd.isAvailable();
  }
  private void beginRequest(Stream stream, HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, boolean connect, Promise<Void> promise) {
    request.remoteAddress = remoteAddress();
    stream.bytesWritten += buf != null ? buf.readableBytes() : 0L;
    HttpRequest nettyRequest = createRequest(request.method, request.uri, request.headers, request.authority, chunked, buf, end);
    synchronized (this) {
      responses.add(stream);
      this.isConnect = connect;
      if (this.metrics != null) {
        stream.metric = this.metrics.requestBegin(request.uri, request);
      }
      VertxTracer tracer = stream.context.tracer();
      if (tracer != null) {
        BiConsumer<String, String> headers = (key, val) -> new HeadersAdaptor(nettyRequest.headers()).add(key, val);
        String operation = request.traceOperation;
        if (operation == null) {
          operation = request.method.name();
        }
        stream.trace = tracer.sendRequest(stream.context, SpanKind.RPC, options.getTracingPolicy(), request, operation, headers, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
      }
    }
    write(nettyRequest, false, promise);
    if (end) {
      endRequest(stream);
    }
  }

  private void writeBufferToChannel(Stream s, ByteBuf buff, boolean end, Promise<Void> listener) {
    s.bytesWritten += buff != null ? buff.readableBytes() : 0L;
    Object msg;
    if (isConnect) {
      msg = buff != null ? buff : Unpooled.EMPTY_BUFFER;
      if (end) {
        write(msg, false, listener)
          .addListener(v -> closeInternal());
      } else {
        write(msg, false);
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
      write(msg, false, listener);
      if (end) {
        endRequest(s);
      }
    }
  }

  private void endRequest(Stream s) {
    Stream next;
    boolean responseEnded;
    synchronized (this) {
      s.requestEnded = true;
      requests.pop();
      next = requests.peek();
      responseEnded = s.responseEnded;
      if (metrics != null) {
        metrics.requestEnd(s.metric, s.bytesWritten);
      }
    }
    flushBytesWritten();
    if (next != null) {
      next.promise.complete((HttpClientStream) next);
    }
    if (responseEnded) {
      s.context.execute(null, s::handleClosed);
      checkLifecycle();
    }
  }

  /**
   * Resets the given {@code stream}.
   *
   * @param stream to reset
   * @return whether the stream should be considered as closed
   */
  private Boolean reset(Stream stream) {
    if (stream.reset) {
      return null;
    }
    stream.reset = true;
    if (!responses.contains(stream)) {
      requests.remove(stream);
      return true;
    }
    close();
    return false;
  }

  private void writeHead(Stream stream, HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, boolean connect, Promise<Void> listener) {
    writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        if (stream.reset) {
          listener.fail("Stream reset");
          return;
        }
        stream.request = request;
        beginRequest(stream, request, chunked, buf, end, connect, listener);
      }
      @Override
      public void cancel(Throwable cause) {
        listener.fail(cause);
      }
    });
  }

  private void writeBuffer(Stream stream, ByteBuf buff, boolean end, Promise<Void> listener) {
    writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        if (stream.reset) {
          listener.fail("Stream reset");
          return;
        }
        writeBufferToChannel(stream, buff, end, listener);
      }

      @Override
      public void cancel(Throwable cause) {
        listener.fail(cause);
      }
    });
  }

  private abstract static class Stream {

    protected final Promise<HttpClientStream> promise;
    protected final ContextInternal context;
    protected final int id;

    private Object trace;
    private Object metric;
    private HttpRequestHead request;
    private HttpResponseHead response;
    private boolean requestEnded;
    private boolean responseEnded;
    private long bytesRead;
    private long bytesWritten;
    private boolean reset;

    Stream(ContextInternal context, Promise<HttpClientStream> promise, int id) {
      this.context = context;
      this.id = id;
      this.promise = promise;
    }

    // Not really elegant... but well
    Object metric() {
      return metric;
    }

    Object trace() {
      return trace;
    }

    abstract void handleContinue();
    abstract void handleEarlyHints(MultiMap headers);
    abstract void handleHead(HttpResponseHead response);
    abstract void handleChunk(Buffer buff);
    abstract void handleEnd(LastHttpContent trailer);
    abstract void handleWriteQueueDrained(Void v);
    abstract void handleException(Throwable cause);
    abstract void handleClosed(Throwable err);

  }

  /**
   * We split the stream class in two classes so that the base {@link #Stream} class defines the (mutable)
   * state managed by the connection and this class defines the state managed by the stream implementation
   */
  private static class StreamImpl extends Stream implements HttpClientStream {

    private final Http1xClientConnection conn;
    private final InboundMessageQueue<Object> queue;
    private boolean closed;
    private Handler<HttpResponseHead> headHandler;
    private Handler<Buffer> chunkHandler;
    private Handler<MultiMap> trailerHandler;
    private Handler<Void> endHandler;
    private Handler<Void> drainHandler;
    private Handler<Void> continueHandler;

    private Handler<MultiMap> earlyHintsHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> closeHandler;

    StreamImpl(ContextInternal context, Http1xClientConnection conn, Promise<HttpClientStream> promise, int id) {
      super(context, promise, id);

      this.conn = conn;
      this.queue = new InboundMessageQueue<>(conn.context.eventLoop(), context.executor()) {
        @Override
        protected void handleResume() {
          conn.doResume();
        }
        @Override
        protected void handlePause() {
          conn.doPause();
        }
        @Override
        protected void handleMessage(Object item) {
          if (item instanceof MultiMap) {
            Handler<MultiMap> handler1 = trailerHandler;
            if (handler1 != null) {
              context.dispatch((MultiMap) item, handler1);
            }
            Handler<Void> handler2 = endHandler;
            if (handler2 != null) {
              context.dispatch(null, handler2);
            }
          } else {
            Buffer buffer = (Buffer) item;
            Handler<Buffer> handler = chunkHandler;
            if (handler != null) {
              context.dispatch(buffer, handler);
            }
          }
        }
      };
    }

    @Override
    public HttpClientStream continueHandler(Handler<Void> handler) {
      continueHandler = handler;
      return this;
    }

    @Override
    public HttpClientStream earlyHintsHandler(Handler<MultiMap> handler) {
      earlyHintsHandler = handler;
      return this;
    }

    @Override
    public StreamImpl drainHandler(Handler<Void> handler) {
      drainHandler = handler;
      return this;
    }

    @Override
    public StreamImpl exceptionHandler(Handler<Throwable> handler) {
      exceptionHandler = handler;
      return this;
    }

    @Override
    public HttpClientStream setWriteQueueMaxSize(int maxSize) {
      conn.doSetWriteQueueMaxSize(maxSize);
      return this;
    }

    @Override
    public boolean writeQueueFull() {
      return conn.writeQueueFull();
    }

    @Override
    public HttpClientStream headHandler(Handler<HttpResponseHead> handler) {
      this.headHandler = handler;
      return this;
    }

    @Override
    public HttpClientStream closeHandler(Handler<Void> handler) {
      closeHandler = handler;
      return this;
    }

    @Override
    public HttpClientStream priorityHandler(Handler<StreamPriorityBase> handler) {
      // No op
      return this;
    }

    @Override
    public HttpClientStream pushHandler(Handler<Http2ClientPush> handler) {
      // No op
      return this;
    }

    @Override
    public HttpClientStream unknownFrameHandler(Handler<HttpFrame> handler) {
      // No op
      return this;
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
    public Object trace() {
      return super.trace();
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
    public ContextInternal context() {
      return context;
    }

    @Override
    public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriorityBase priority, boolean connect) {
      PromiseInternal<Void> promise = context.promise();
      conn.writeHead(this, request, chunked, buf, end, connect, promise);
      return promise.future();
    }

    @Override
    public Future<Void> write(ByteBuf buff, boolean end) {
      if (buff != null || end) {
        Promise<Void> listener = context.promise();
        conn.writeBuffer(this, buff, end, listener);
        return listener.future();
      } else {
        throw new IllegalStateException("???");
      }
    }

    @Override
    public Future<Void> writeFrame(int type, int flags, ByteBuf payload) {
      throw new IllegalStateException("Cannot write an HTTP/2 frame over an HTTP/1.x connection");
    }

    @Override
    public HttpClientStream pause() {
      queue.pause();
      return this;
    }

    @Override
    public HttpClientStream resume() {
      queue.fetch(Long.MAX_VALUE);
      return this;
    }

    @Override
    public HttpClientStream fetch(long amount) {
      queue.fetch(amount);
      return this;
    }

    @Override
    public Future<Void> reset(Throwable cause) {
      Promise<Void> promise = context.promise();
      EventLoop eventLoop = conn.context.nettyEventLoop();
      if (eventLoop.inEventLoop()) {
        reset(cause, promise);
      } else {
        eventLoop.execute(() -> reset(cause, promise));
      }
      return promise.future();
    }

    private void reset(Throwable cause, Promise<Void> promise) {
      Boolean removed = conn.reset(this);
      if (removed == null) {
        promise.fail("Stream already reset");
      } else {
        if (removed) {
          context.execute(cause, this::handleClosed);
        } else {
          context.execute(cause, this::handleException);
        }
        promise.complete();      }
    }

    @Override
    public StreamPriorityBase priority() {
      return null;
    }

    @Override
    public HttpClientStream updatePriority(StreamPriorityBase streamPriority) {
      return this;
    }

    @Override
    void handleWriteQueueDrained(Void v) {
      Handler<Void> handler;
      synchronized (conn) {
        handler = drainHandler;
      }
      if (handler != null) {
        context.dispatch(handler);
      }
    }

    void handleContinue() {
      Handler<Void> handler = continueHandler;
      if (handler != null) {
        context.emit(null, handler);
      }
    }

    void handleEarlyHints(MultiMap headers) {
      Handler<MultiMap> handler = earlyHintsHandler;
      if (handler != null) {
        context.emit(headers, handler);
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
    public HttpClientStream handler(Handler<Buffer> handler) {
      chunkHandler = handler;
      return this;
    }

    @Override
    public HttpClientStream trailersHandler(Handler<MultiMap> handler) {
      trailerHandler = handler;
      return this;
    }

    @Override
    public HttpClientStream endHandler(Handler<Void> handler) {
      endHandler = handler;
      return this;
    }

    void handleChunk(Buffer buff) {
      queue.write(buff);
    }

    void handleEnd(LastHttpContent trailer) {
      queue.write(new HeadersAdaptor(trailer.trailingHeaders()));
    }

    void handleException(Throwable cause) {
      Handler<Throwable> handler = exceptionHandler;
      if (handler != null) {
        context.emit(cause, handler);
      }
    }

    @Override
    void handleClosed(Throwable err) {
      if (err != null) {
        handleException(err);
        promise.tryFail(err);
      }
      if (!closed) {
        closed = true;
        Handler<Void> handler = closeHandler;
        if (handler != null) {
          context.emit(null, handler);
        }
      }
    }

    @Override
    public StreamPriorityBase createDefaultStreamPriority() {
      return HttpUtils.DEFAULT_STREAM_PRIORITY;
    }
  }

  @Override
  protected void handleShutdown(Object reason, long timeout, TimeUnit unit, ChannelPromise promise) {
    super.handleShutdown(reason, timeout, unit, promise);
    checkLifecycle();
  }

  private boolean checkLifecycle() {
    if (wantClose || (shutdownInitiated && requests.isEmpty() && responses.isEmpty())) {
      closeInternal();
      return true;
    } else if (!isConnect) {
      expirationTimestamp = expirationTimestampOf(keepAliveTimeout);
    }
    return false;
  }

  @Override
  protected void handleClose(Object reason, ChannelPromise promise) {
    // Maybe move to handleShutdown
    if (!evicted) {
      evicted = true;
      if (evictionHandler != null) {
        evictionHandler.handle(null);
      }
    }
    super.handleClose(reason, promise);
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
      WebSocketFrame frame = (WebSocketFrame) msg;
      if (pendingFrames == null) {
        pendingFrames = new ArrayDeque<>();
      }
      // Todo: use the new feature to park frames within the handler later
      pendingFrames.add(frame);
    } else {
      invalidMessageHandler.handle(msg);
      fail(new VertxException("Received an invalid message: " + msg.getClass().getName()));
    }
  }

  private void handleHttpMessage(HttpObject obj) {
    Stream stream;
    synchronized (this) {
      stream = responses.peekFirst();
    }
    if (stream == null) {
      invalidMessageHandler.handle(obj);
      fail(new VertxException("Received an HTTP message with no request in progress: " + obj.getClass().getName()));
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
    // How can we handle future undefined 1xx informational response codes?
    if (response.statusCode == HttpResponseStatus.CONTINUE.code()) {
      stream.handleContinue();
    } else if (response.statusCode == HttpResponseStatus.EARLY_HINTS.code()) {
      stream.handleEarlyHints(response.headers);
    } else {
      HttpRequestHead request;
      synchronized (this) {
        request = stream.request;
        stream.response = response;
        if (metrics != null) {
          metrics.responseBegin(stream.metric, response);
        }
      }
      stream.handleHead(response);
      if (isConnect) {
        if ((request.method == HttpMethod.CONNECT &&
             response.statusCode == 200) || (
             request.method == HttpMethod.GET &&
             request.headers != null && request.headers.contains(CONNECTION, UPGRADE, true) &&
             response.statusCode == 101)) {
          removeChannelHandlers();
        } else {
          isConnect = false;
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
    invalidMessageHandler = INVALID_MSG_HANDLER;
    try {
      pipeline.remove("codec");
    } finally {
      invalidMessageHandler = prev;
    }
  }

  private void handleResponseChunk(Stream stream, ByteBuf chunk) {
    Buffer buff = BufferInternal.safeBuffer(chunk);
    int len = buff.length();
    stream.bytesRead += len;
    if (!stream.reset) {
      stream.handleChunk(buff);
    }
  }

  private void handleResponseEnd(Stream stream, LastHttpContent trailer) {
    boolean check;
    HttpResponseHead response ;
    synchronized (this) {
      response = stream.response;
      if (response == null) {
        // 100-continue
        return;
      }
      responses.pop();
      HttpRequestHead request = stream.request;
      if ((request.method != HttpMethod.CONNECT && response.statusCode != 101)) {
        // See https://tools.ietf.org/html/rfc7230#section-6.3
        String responseConnectionHeader = response.headers.get(HttpHeaderNames.CONNECTION);
        String requestConnectionHeader = request.headers != null ? request.headers.get(HttpHeaderNames.CONNECTION) : null;
        // We don't need to protect against concurrent changes on forceClose as it only goes from false -> true
        boolean close = !options.isKeepAlive();
        if (HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(responseConnectionHeader) || HttpHeaderValues.CLOSE.contentEqualsIgnoreCase(requestConnectionHeader)) {
          // In all cases, if we have a close connection option then we SHOULD NOT treat the connection as persistent
          close = true;
        } else if (response.version == HttpVersion.HTTP_1_0 && !HttpHeaderValues.KEEP_ALIVE.contentEqualsIgnoreCase(responseConnectionHeader)) {
          // In the HTTP/1.0 case both request/response need a keep-alive connection header the connection to be persistent
          // currently Vertx forces the Connection header if keepalive is enabled for 1.0
          close = true;
        }
        this.wantClose = close;
        String keepAliveHeader = response.headers.get(HttpHeaderNames.KEEP_ALIVE);
        if (keepAliveHeader != null) {
          int timeout = HttpUtils.parseKeepAliveHeaderTimeout(keepAliveHeader);
          if (timeout != -1) {
            this.keepAliveTimeout = timeout;
          }
        }
      }
      stream.responseEnded = true;
      check = requests.peek() != stream;
    }
    VertxTracer tracer = stream.context.tracer();
    if (tracer != null) {
      tracer.receiveResponse(stream.context, response, stream.trace, null, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
    }
    if (metrics != null) {
      metrics.responseEnd(stream.metric, stream.bytesRead);
    }
    flushBytesRead();
    if (check) {
      checkLifecycle();
    }
    lastResponseReceivedTimestamp = System.currentTimeMillis();
    if (!stream.reset) {
      stream.handleEnd(trailer);
    }
    if (stream.requestEnded) {
      stream.handleClosed(null);
    }
  }

  public HttpClientMetrics metrics() {
    return client.metrics();
  }

  /**
   * @return a future of a paused WebSocket
   */
  synchronized void toWebSocket(
    ContextInternal context,
    String requestURI,
    MultiMap headers,
    boolean allowOriginHeader,
    WebSocketClientOptions options,
    WebSocketVersion vers,
    List<String> subProtocols,
    long handshakeTimeout,
    boolean registerWriteHandlers,
    int maxWebSocketFrameSize,
    Promise<WebSocket> promise) {
    try {
      URI wsuri = new URI(requestURI);
      if (!wsuri.isAbsolute()) {
        // Netty requires an absolute url
        wsuri = new URI((ssl ? "https:" : "http:") + "//" + server.host() + ":" + server.port() + requestURI);
      }
      io.netty.handler.codec.http.websocketx.WebSocketVersion version =
         io.netty.handler.codec.http.websocketx.WebSocketVersion.valueOf((vers == null ?
           io.netty.handler.codec.http.websocketx.WebSocketVersion.V13 : vers).toString());
      HttpHeaders nettyHeaders;
      if (headers != null) {
        nettyHeaders = new DefaultHttpHeaders();
        for (Map.Entry<String, String> entry: headers) {
          nettyHeaders.add(entry.getKey(), entry.getValue());
        }
      } else {
        nettyHeaders = null;
      }

      long timer;
      if (handshakeTimeout > 0L) {
        timer = vertx.setTimer(handshakeTimeout, id -> closeInternal());
      } else {
        timer = -1;
      }

      ChannelPipeline p = chctx.channel().pipeline();
      ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = initializeWebSocketExtensionHandshakers(options);
      if (!extensionHandshakers.isEmpty()) {
        p.addBefore("handler", "webSocketsExtensionsHandler", new WebSocketClientExtensionHandler(
          extensionHandshakers.toArray(new WebSocketClientExtensionHandshaker[0])));
      }

      String subp = null;
      if (subProtocols != null) {
        subp = String.join(",", subProtocols);
      }
      WebSocketClientHandshaker handshaker = newHandshaker(
        wsuri,
        version,
        subp,
        !extensionHandshakers.isEmpty(),
        allowOriginHeader,
        nettyHeaders,
        maxWebSocketFrameSize,
        !options.isSendUnmaskedFrames());

      io.netty.util.concurrent.Promise<HttpHeaders> upgrade = chctx.executor().newPromise();
      WebSocketHandshakeInboundHandler handshakeInboundHandler = new WebSocketHandshakeInboundHandler(handshaker, upgrade);
      p.addBefore("handler", "handshakeCompleter", handshakeInboundHandler);
      upgrade.addListener((GenericFutureListener<io.netty.util.concurrent.Future<HttpHeaders>>) future -> {
        if (timer > -1L) {
          vertx.cancelTimer(timer);
        }
        if (future.isSuccess()) {

          VertxHandler<WebSocketConnectionImpl> handler = VertxHandler.create(ctx -> {
            WebSocketConnectionImpl conn = new WebSocketConnectionImpl(context, ctx, false, TimeUnit.SECONDS.toMillis(options.getClosingTimeout()), client.metrics());
            WebSocketImpl webSocket = new WebSocketImpl(
              context,
              conn,
              version != V00,
              options.getMaxFrameSize(),
              options.getMaxMessageSize(),
              registerWriteHandlers);
            conn.webSocket(webSocket);
            conn.metric(Http1xClientConnection.this.metric());
            return conn;
          });

          ChannelPipeline pipeline = chctx.pipeline();
          pipeline.replace(VertxHandler.class, "handler", handler);

          WebSocketImpl ws = (WebSocketImpl) handler.getConnection().webSocket();
          ws.headers(new HeadersAdaptor(future.getNow()));
          ws.subProtocol(handshaker.actualSubprotocol());
          ws.registerHandler(vertx.eventBus());

          HttpClientMetrics metrics = client.metrics();
          if (metrics != null) {
            ws.setMetric(metrics.connected(ws));
          }
          ws.pause();
          Deque<WebSocketFrame> toResubmit = pendingFrames;
          if (toResubmit != null) {
            pendingFrames = null;
            WebSocketFrame frame;
            while ((frame = toResubmit.poll()) != null) {
              handler.getConnection().handleWsFrame(frame);
            }
          }
          promise.complete(ws);
        } else {
          closeInternal();
          promise.fail(future.cause());
        }
      });
    } catch (Exception e) {
      handleException(e);
    }
  }

  static WebSocketClientHandshaker newHandshaker(
    URI webSocketURL, io.netty.handler.codec.http.websocketx.WebSocketVersion version, String subprotocol,
    boolean allowExtensions, boolean allowOriginHeader, HttpHeaders customHeaders, int maxFramePayloadLength,
    boolean performMasking) {
    WebSocketDecoderConfig config = WebSocketDecoderConfig.newBuilder()
      .expectMaskedFrames(false)
      .allowExtensions(allowExtensions)
      .maxFramePayloadLength(maxFramePayloadLength)
      .allowMaskMismatch(false)
      .closeOnProtocolViolation(false)
      .build();
    if (version == V13) {
      return new WebSocketClientHandshaker13(
        webSocketURL, V13, subprotocol, allowExtensions, customHeaders,
        maxFramePayloadLength, performMasking, false, -1) {
        @Override
        protected WebSocketFrameDecoder newWebsocketDecoder() {
          return new WebSocket13FrameDecoder(config);
        }

        @Override
        protected FullHttpRequest newHandshakeRequest() {
          FullHttpRequest request = super.newHandshakeRequest();
          if (!allowOriginHeader) {
            request.headers().remove(ORIGIN);
          }
          return request;
        }
      };
    }
    if (version == V08) {
      return new WebSocketClientHandshaker08(
        webSocketURL, V08, subprotocol, allowExtensions, customHeaders,
        maxFramePayloadLength, performMasking, false, -1) {
        @Override
        protected WebSocketFrameDecoder newWebsocketDecoder() {
          return new WebSocket08FrameDecoder(config);
        }

        @Override
        protected FullHttpRequest newHandshakeRequest() {
          FullHttpRequest request = super.newHandshakeRequest();
          if (!allowOriginHeader) {
            request.headers().remove(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN);
          }
          return request;
        }
      };
    }
    if (version == V07) {
      return new WebSocketClientHandshaker07(
        webSocketURL, V07, subprotocol, allowExtensions, customHeaders,
        maxFramePayloadLength, performMasking, false, -1) {
        @Override
        protected WebSocketFrameDecoder newWebsocketDecoder() {
          return new WebSocket07FrameDecoder(config);
        }

        @Override
        protected FullHttpRequest newHandshakeRequest() {
          FullHttpRequest request = super.newHandshakeRequest();
          if (!allowOriginHeader) {
            request.headers().remove(HttpHeaderNames.SEC_WEBSOCKET_ORIGIN);
          }
          return request;
        }
      };
    }
    if (version == V00) {
      return new WebSocketClientHandshaker00(
        webSocketURL, V00, subprotocol, customHeaders, maxFramePayloadLength, -1) {
        @Override
        protected FullHttpRequest newHandshakeRequest() {
          FullHttpRequest request = super.newHandshakeRequest();
          if (!allowOriginHeader) {
            request.headers().remove(ORIGIN);
          }
          return request;
        }
      };
    }

    throw new WebSocketHandshakeException("Protocol version " + version + " not supported.");
  }

  ArrayList<WebSocketClientExtensionHandshaker> initializeWebSocketExtensionHandshakers(WebSocketClientOptions options) {
    ArrayList<WebSocketClientExtensionHandshaker> extensionHandshakers = new ArrayList<>();
    if (options.getTryUsePerFrameCompression()) {
      extensionHandshakers.add(new DeflateFrameClientExtensionHandshaker(options.getCompressionLevel(),
        false));
    }

    if (options.getTryUsePerMessageCompression()) {
      extensionHandshakers.add(new PerMessageDeflateClientExtensionHandshaker(options.getCompressionLevel(),
        ZlibCodecFactory.isSupportingWindowSizeAndMemLevel(), PerMessageDeflateServerExtensionHandshaker.MAX_WINDOW_SIZE,
        options.getCompressionAllowClientNoContext(), options.getCompressionRequestServerNoContext()));
    }

    return extensionHandshakers;
  }

  @Override
  protected void handleWriteQueueDrained() {
    Stream s = requests.peek();
    if (s != null) {
      s.context.execute(s::handleWriteQueueDrained);
    } else {
      super.handleWriteQueueDrained();
    }
  }

  protected void handleClosed() {
    super.handleClosed();
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
    List<Stream> allocatedStreams;
    List<Stream> sentStreams;
    synchronized (this) {
      sentStreams = new ArrayList<>(responses);
      allocatedStreams = new ArrayList<>(requests);
      allocatedStreams.removeAll(responses);
    }
    for (Stream stream : allocatedStreams) {
      stream.context.execute(HttpUtils.CONNECTION_CLOSED_EXCEPTION, stream::handleClosed);
    }
    for (Stream stream : sentStreams) {
      if (metrics != null) {
        metrics.requestReset(stream.metric);
      }
      Object trace = stream.trace;
      VertxTracer tracer = stream.context.tracer();
      if (tracer != null && trace != null) {
        tracer.receiveResponse(stream.context, null, trace, HttpUtils.CONNECTION_CLOSED_EXCEPTION, TagExtractor.empty());
      }
      stream.context.execute(HttpUtils.CONNECTION_CLOSED_EXCEPTION, stream::handleClosed);
    }
  }

  protected void handleIdle(IdleStateEvent event) {
    synchronized (this) {
      if (responses.isEmpty() && requests.isEmpty()) {
        return;
      }
    }
    super.handleIdle(event);
  }

  @Override
  public void handleException(Throwable e) {
    super.handleException(e);
    LinkedHashSet<Stream> allStreams = new LinkedHashSet<>();
    synchronized (this) {
      allStreams.addAll(requests);
      allStreams.addAll(responses);
    }
    for (Stream stream : allStreams) {
      stream.handleException(e);
    }
  }

  @Override
  public Future<HttpClientStream> createStream(ContextInternal context) {
    PromiseInternal<HttpClientStream> promise = context.promise();
    createStream(context, promise);
    return promise.future();
  }

  private void createStream(ContextInternal context, Promise<HttpClientStream> promise) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      Object result;
      synchronized (this) {
        if (!closed) {
          if (requests.size() < concurrency()) {
            StreamImpl stream = new StreamImpl(context, this, promise, seq++);
            requests.add(stream);
            if (requests.size() > 1) {
              return;
            }
            result = stream;
          } else {
            result = new VertxException("Pipelining limit exceeded");
          }
        } else {
          result = HttpUtils.CONNECTION_CLOSED_EXCEPTION;
        }
      }
      if (result instanceof HttpClientStream) {
        promise.complete((HttpClientStream) result);
      } else {
        promise.fail((Throwable) result);
      }
    } else {
      eventLoop.execute(() -> {
        createStream(context, promise);
      });
    }
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return lastResponseReceivedTimestamp;
  }

  @Override
  public boolean isValid() {
    long now = System.currentTimeMillis();
    return now <= expirationTimestamp && now <= lifetimeEvictionTimestamp;
  }

  /**
   * Compute the expiration timeout of the connection, relative to the current time.
   *
   * @param timeout the timeout
   * @return the expiration timestamp
   */
  private static long expirationTimestampOf(long timeout) {
    return timeout > 0 ? System.currentTimeMillis() + timeout * 1000 : Long.MAX_VALUE;
  }
}
