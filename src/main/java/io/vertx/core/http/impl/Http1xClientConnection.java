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
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.compression.Brotli;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandler;
import io.netty.handler.codec.http.websocketx.extensions.WebSocketClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.DeflateFrameClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateClientExtensionHandshaker;
import io.netty.handler.codec.http.websocketx.extensions.compression.PerMessageDeflateServerExtensionHandshaker;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.buffer.impl.BufferInternal;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.impl.*;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.metrics.HttpClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.streams.impl.InboundBuffer;

import java.net.URI;
import java.util.*;
import java.util.function.BiConsumer;

import static io.netty.handler.codec.http.websocketx.WebSocketVersion.*;
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

  private final HttpClientBase client;
  private final HttpClientOptions options;
  private final boolean ssl;
  private final SocketAddress server;
  private final HostAndPort authority;
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
  private long readWindow;
  private Deque<WebSocketFrame> pendingFrames;

  private long lastResponseReceivedTimestamp;

  Http1xClientConnection(HttpVersion version,
                         HttpClientBase client,
                         ChannelHandlerContext chctx,
                         boolean ssl,
                         SocketAddress server,
                         HostAndPort authority,
                         ContextInternal context,
                         ClientMetrics metrics) {
    super(context, chctx);
    this.client = client;
    this.options = client.options();
    this.ssl = ssl;
    this.server = server;
    this.authority = authority;
    this.metrics = metrics;
    this.version = version;
    this.readWindow = 0L;
    this.highWaterMark = chctx.channel().config().getWriteBufferHighWaterMark();
    this.lowWaterMark = chctx.channel().config().getWriteBufferLowWaterMark();
    this.keepAliveTimeout = options.getKeepAliveTimeout();
    this.expirationTimestamp = expirationTimestampOf(keepAliveTimeout);
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
  protected void handleEvent(Object evt) {
    if (evt instanceof ShutdownEvent) {
      ShutdownEvent shutdown = (ShutdownEvent) evt;
      shutdown(shutdown.timeUnit().toMillis(shutdown.timeout()));
    } else {
      super.handleEvent(evt);
    }
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
        request = new AssembledFullHttpRequest(request, buf);
      } else {
        request = new AssembledFullHttpRequest(request);
      }
    } else if (buf != null) {
      request = new AssembledHttpRequest(request, buf);
    }
    return request;
  }

  static CharSequence determineCompressionAcceptEncoding() {
    if (isBrotliAvailable()) {
      return DEFLATE_GZIP_BR;
    } else {
      return DEFLATE_GZIP;
    }
  }

  // Encapsulated in a method, so GraalVM can substitute it
  private static boolean isBrotliAvailable() {
    return Brotli.isAvailable();
  }

  private void beginRequest(Stream stream, HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, boolean connect, PromiseInternal<Void> promise) {
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

  private void writeBuffer(Stream s, ByteBuf buff, boolean end, FutureListener<Void> listener) {
    s.bytesWritten += buff != null ? buff.readableBytes() : 0L;
    Object msg;
    if (isConnect) {
      msg = buff != null ? buff : Unpooled.EMPTY_BUFFER;
      if (end) {
        write(msg, false, channelFuture()
          .addListener(listener)
          .addListener(v -> close())
        );
      } else {
        write(msg, false, voidPromise);
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
    boolean inflight;
    synchronized (this) {
      inflight = responses.contains(stream) || stream.responseEnded;
      if (!inflight) {
        requests.remove(stream);
      }
      close = inflight;
    }
    checkLifecycle();
    return !inflight;
  }

  private void receiveBytes(int len) {
    boolean le = readWindow <= highWaterMark;
    readWindow += len;
    boolean gt = readWindow > highWaterMark;
    if (le && gt) {
      doPause();
    }
  }

  private void ackBytes(int len) {
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      boolean gt = readWindow > lowWaterMark;
      readWindow -= len;
      boolean le = readWindow <= lowWaterMark;
      if (gt && le) {
        doResume();
      }
    } else {
      eventLoop.execute(() -> ackBytes(len));
    }
  }

  private void writeHead(Stream stream, HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, boolean connect, PromiseInternal<Void> handler) {
    writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        stream.request = request;
        beginRequest(stream, request, chunked, buf, end, connect, handler);
      }
      @Override
      public void cancel(Throwable cause) {
        handler.fail(cause);
      }
    });
  }

  private void writeBuffer(Stream stream, ByteBuf buff, boolean end, PromiseInternal<Void> listener) {
    writeToChannel(new MessageWrite() {
      @Override
      public void write() {
        writeBuffer(stream, buff, end, (FutureListener<Void>)listener);
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
    private boolean responseEnded;
    private long bytesRead;
    private long bytesWritten;


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
    private boolean closed;
    private Handler<HttpResponseHead> headHandler;
    private Handler<Buffer> chunkHandler;
    private Handler<MultiMap> endHandler;
    private Handler<Void> drainHandler;
    private Handler<Void> continueHandler;

    private Handler<MultiMap> earlyHintsHandler;
    private Handler<Throwable> exceptionHandler;
    private Handler<Void> closeHandler;

    StreamImpl(ContextInternal context, Http1xClientConnection conn, Promise<HttpClientStream> promise, int id) {
      super(context, promise, id);

      this.conn = conn;
      this.queue = new InboundBuffer<>(context, 5)
        .handler(item -> {
          if (!reset) {
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
          }
        })
        .exceptionHandler(context::reportException);
    }

    @Override
    public void continueHandler(Handler<Void> handler) {
      continueHandler = handler;
    }

    @Override
    public void earlyHintsHandler(Handler<MultiMap> handler) {
      earlyHintsHandler = handler;
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
    public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
      return null;
    }

    @Override
    public boolean writeQueueFull() {
      return conn.writeQueueFull();
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
    public ContextInternal getContext() {
      return context;
    }

    @Override
    public Future<Void> writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect) {
      PromiseInternal<Void> promise = context.promise();
      conn.writeHead(this, request, chunked, buf, end, connect, promise);
      return promise.future();
    }

    @Override
    public Future<Void> writeBuffer(ByteBuf buff, boolean end) {
      if (buff != null || end) {
        PromiseInternal<Void> listener = context.promise();
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
    public void doSetWriteQueueMaxSize(int size) {
      conn.doSetWriteQueueMaxSize(size);
    }

    @Override
    public boolean isNotWritable() {
      return conn.writeQueueFull();
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
      if (continueHandler != null) {
        continueHandler.handle(null);
      }
    }

    void handleEarlyHints(MultiMap headers) {
      if (earlyHintsHandler != null) {
        earlyHintsHandler.handle(headers);
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
      tryClose();
    }

    void handleException(Throwable cause) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(cause);
      }
    }

    @Override
    void handleClosed() {
      handleException(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
      tryClose();
    }

    /**
     * Attempt to close the stream.
     */
    private void tryClose() {
      if (!closed) {
        closed = true;
        if (closeHandler != null) {
          closeHandler.handle(null);
        }
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
      WebSocketFrame frame = (WebSocketFrame) msg;
      if (webSocket == null) {
        if (pendingFrames == null) {
          pendingFrames = new ArrayDeque<>();
        }
        pendingFrames.add(frame);
      } else {
        handleWsFrame(frame);
      }
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
    // How can we handle future undefined 1xx informational response codes?
    if (response.statusCode == HttpResponseStatus.CONTINUE.code()) {
      stream.context.execute(null, v -> stream.handleContinue());
    } else if (response.statusCode == HttpResponseStatus.EARLY_HINTS.code()) {
      stream.context.execute(null, v -> stream.handleEarlyHints(response.headers));
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
    Buffer buff = BufferInternal.buffer(VertxHandler.safeBuffer(chunk));
    int len = buff.length();
    receiveBytes(len);
    stream.bytesRead += len;
    stream.context.execute(buff, stream::handleChunk);
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
        this.close = close;
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
    VertxTracer tracer = context.tracer();
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
    stream.context.execute(trailer, stream::handleEnd);
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
    WebsocketVersion vers,
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

      long timer;
      if (handshakeTimeout > 0L) {
        timer = vertx.setTimer(handshakeTimeout, id -> close());
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
        if (timer > 0L) {
          vertx.cancelTimer(timer);
        }
        if (future.isSuccess()) {
          WebSocketImpl ws = new WebSocketImpl(
            context,
            Http1xClientConnection.this,
            version != V00,
            options.getClosingTimeout(),
            options.getMaxFrameSize(),
            options.getMaxMessageSize(),
            registerWriteHandlers);
          ws.headers(new HeadersAdaptor(future.getNow()));
          ws.subProtocol(handshaker.actualSubprotocol());
          ws.registerHandler(vertx.eventBus());
          webSocket = ws;
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
              handleWsFrame(frame);
            }
          }
          promise.complete(ws);
        } else {
          close();
          promise.fail(future.cause());
        }
      });
    } catch (Exception e) {
      handleException(e);
    }
  }

  static WebSocketClientHandshaker newHandshaker(
    URI webSocketURL, WebSocketVersion version, String subprotocol,
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
  protected void writeQueueDrained() {
    Stream s = requests.peek();
    if (s != null) {
      s.context.execute(s::handleWriteQueueDrained);
    } else {
      super.writeQueueDrained();
    }
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
    List<Stream> allocatedStreams;
    List<Stream> sentStreams;
    synchronized (this) {
      ws = webSocket;
      sentStreams = new ArrayList<>(responses);
      allocatedStreams = new ArrayList<>(requests);
      allocatedStreams.removeAll(responses);
    }
    if (ws != null) {
      ws.handleConnectionClosed();
    }
    for (Stream stream : allocatedStreams) {
      stream.context.execute(null, v -> stream.handleClosed());
    }
    for (Stream stream : sentStreams) {
      if (metrics != null) {
        metrics.requestReset(stream.metric);
      }
      Object trace = stream.trace;
      if (tracer != null && trace != null) {
        tracer.receiveResponse(stream.context, null, trace, HttpUtils.CONNECTION_CLOSED_EXCEPTION, TagExtractor.empty());
      }
      stream.context.execute(null, v -> stream.handleClosed());
    }
  }

  protected void handleIdle(IdleStateEvent event) {
    synchronized (this) {
      if (webSocket == null && responses.isEmpty() && requests.isEmpty()) {
        return;
      }
    }
    super.handleIdle(event);
  }

  @Override
  protected void handleException(Throwable e) {
    super.handleException(e);
    WebSocketImpl ws;
    LinkedHashSet<Stream> allStreams = new LinkedHashSet<>();
    synchronized (this) {
      ws = webSocket;
      allStreams.addAll(requests);
      allStreams.addAll(responses);
    }
    if (ws != null) {
      ws.handleException(e);
    }
    for (Stream stream : allStreams) {
      stream.handleException(e);
    }
  }

  @Override
  public Future<HttpClientRequest> createRequest(ContextInternal context) {
    return ((HttpClientImpl)client).createRequest(this, context);
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
      synchronized (this) {
        if (!closed) {
          StreamImpl stream = new StreamImpl(context, this, promise, seq++);
          requests.add(stream);
          if (requests.size() == 1) {
            stream.promise.complete(stream);
          }
          return;
        }
      }
      promise.fail(HttpUtils.CONNECTION_CLOSED_EXCEPTION);
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
    return expirationTimestamp == 0 || System.currentTimeMillis() <= expirationTimestamp;
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
