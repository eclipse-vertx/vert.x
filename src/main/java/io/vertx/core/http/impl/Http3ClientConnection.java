package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http2.Http2Exception;
import io.netty.incubator.codec.http3.DefaultHttp3Headers;
import io.netty.incubator.codec.http3.Http3;
import io.netty.incubator.codec.http3.Http3DataFrame;
import io.netty.incubator.codec.http3.Http3Headers;
import io.netty.incubator.codec.http3.Http3HeadersFrame;
import io.netty.incubator.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.incubator.codec.quic.QuicChannel;
import io.netty.incubator.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GenericFutureListener;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpFrame;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.http.StreamResetException;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.http.impl.headers.Http3HeadersAdaptor;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx.core.spi.metrics.ClientMetrics;
import io.vertx.core.spi.tracing.SpanKind;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.streams.WriteStream;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.function.BiConsumer;


public class Http3ClientConnection extends Http3ConnectionBase implements HttpClientConnection {

  public ClientMetrics metrics;
  private HttpVersion version;
  private boolean isConnect;
  private HttpClientImpl client;
  private HttpClientOptions options;
  private Deque<Stream> requests = new ArrayDeque<>();
  private Deque<Stream> responses = new ArrayDeque<>();
  private boolean closed;
  private boolean evicted;

  private long writeWindow;
  private boolean writeOverflow;


  private PromiseInternal<HttpClientConnection> promise;
  private boolean upgrade;
  private Object socketMetric;


  public QuicStreamChannel quicStreamChannel;
  public QuicChannel quicChannel;

  public Http3ClientConnection(QuicChannel quicChannel, QuicStreamChannel quicStreamChannel, ChannelHandlerContext ctx, PromiseInternal<HttpClientConnection> promise, HttpClientImpl client, ClientMetrics metrics, EventLoopContext context, boolean upgrade, Object socketMetric) {
    super(context, ctx);
    this.promise = promise;
    this.client = client;
    this.metrics = metrics;
    this.upgrade = upgrade;
    this.socketMetric = socketMetric;

    this.quicStreamChannel = quicStreamChannel;
    this.quicChannel = quicChannel;
  }

  @Override
  public HttpClientConnection evictionHandler(Handler<Void> handler) {
    return null;
  }

  @Override
  public HttpClientConnection concurrencyChangeHandler(Handler<Long> handler) {
    return null;
  }

  @Override
  public long concurrency() {
    return 5;
  }

  @Override
  public void createStream(ContextInternal context, Handler<AsyncResult<HttpClientStream>> handler) {
    Future<HttpClientStream> fut;
    synchronized (this) {
      try {
        StreamImpl stream = createStream(context);
        fut = Future.succeededFuture(stream);
      } catch (Exception e) {
        fut = Future.failedFuture(e);
      }
    }
    context.emit(fut, handler);
  }
  private StreamImpl createStream(ContextInternal context) {
    return new StreamImpl(this, context, false);
  }


  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public long lastResponseReceivedTimestamp() {
    return 0;
  }














  static abstract class Stream extends VertxHttp3Stream {

    private final boolean push;
    private HttpResponseHead response;
    protected Object metric;
    protected Object trace;
    private boolean requestEnded;
    private boolean responseEnded;
    protected Handler<HttpResponseHead> headHandler;
    protected Handler<Buffer> chunkHandler;
    protected Handler<MultiMap> endHandler;
    protected Handler<StreamPriority> priorityHandler;
    protected Handler<Void> drainHandler;
    protected Handler<Void> continueHandler;
    protected Handler<MultiMap> earlyHintsHandler;
    protected Handler<HttpFrame> unknownFrameHandler;
    protected Handler<Throwable> exceptionHandler;
    protected Handler<HttpClientPush> pushHandler;
    protected Handler<Void> closeHandler;
    protected long writeWindow;
    protected final long windowSize;

    Stream(Http3ClientConnection conn, ContextInternal context, boolean push) {
      super(conn, context);

      this.push = push;
      this.windowSize = conn.getWindowSize();
    }

    void onContinue() {
      context.emit(null, v -> handleContinue());
    }

    void onEarlyHints(MultiMap headers) {
      context.emit(null, v -> handleEarlyHints(headers));
    }

    abstract void handleContinue();

    abstract void handleEarlyHints(MultiMap headers);

    public Object metric() {
      return metric;
    }

    public Object trace() {
      return trace;
    }

    @Override
    void doWriteData(ByteBuf chunk, boolean end, Handler<AsyncResult<Void>> handler) {
      super.doWriteData(chunk, end, handler);
    }

    @Override
    void doWriteHeaders(Http3Headers headers, boolean end, boolean checkFlush, Handler<AsyncResult<Void>> handler) {
      isConnect = "CONNECT".contentEquals(headers.method());
      super.doWriteHeaders(headers, end, checkFlush, handler);
    }

    @Override
    protected void doWriteReset(long code) {
      if (!requestEnded || !responseEnded) {
        super.doWriteReset(code);
      }
    }

    protected void endWritten() {
      requestEnded = true;
      if (conn.metrics != null) {
        conn.metrics.requestEnd(metric, bytesWritten());
      }
      //TODO : review me!
      conn.quicStreamChannel.flush();
    }

    @Override
    void onEnd(MultiMap trailers) {
      conn.metricsEnd(this);
      responseEnded = true;
      super.onEnd(trailers);
    }

    @Override
    void onReset(long code) {
      if (conn.metrics != null) {
        conn.metrics.requestReset(metric);
      }
      super.onReset(code);
    }

    @Override
    void onHeaders(Http3Headers headers, StreamPriority streamPriority) {
      if (streamPriority != null) {
        priority(streamPriority);
      }
      if (response == null) {
        int status;
        String statusMessage;
        try {
          status = Integer.parseInt(headers.status().toString());
          statusMessage = HttpResponseStatus.valueOf(status).reasonPhrase();
        } catch (Exception e) {
          handleException(e);
          writeReset(0x01 /* PROTOCOL_ERROR */);
          return;
        }
        if (status == 100) {
          onContinue();
          return;
        } else if (status == 103) {
          MultiMap headersMultiMap = HeadersMultiMap.httpHeaders();
          removeStatusHeaders(headers);
          for (Map.Entry<CharSequence, CharSequence> header : headers) {
            headersMultiMap.add(header.getKey(), header.getValue());
          }
          onEarlyHints(headersMultiMap);
          return;
        }
        response = new HttpResponseHead(
          HttpVersion.HTTP_2,
          status,
          statusMessage,
          new Http3HeadersAdaptor(headers));
        removeStatusHeaders(headers);

        if (conn.metrics != null) {
          conn.metrics.responseBegin(metric, response);
        }

        if (headHandler != null) {
          context.emit(response, headHandler);
        }
      }
    }

    private void removeStatusHeaders(Http3Headers headers) {
      headers.remove(":status");
    }

    @Override
    void onClose() {
      if (conn.metrics != null) {
        if (!requestEnded || !responseEnded) {
          conn.metrics.requestReset(metric);
        }
      }
      VertxTracer tracer = context.tracer();
      if (tracer != null && trace != null) {
        VertxException err;
        if (responseEnded && requestEnded) {
          err = null;
        } else {
          err = HttpUtils.STREAM_CLOSED_EXCEPTION;
        }
        tracer.receiveResponse(context, response, trace, err, HttpUtils.CLIENT_RESPONSE_TAG_EXTRACTOR);
      }
      if (!responseEnded) {
        // NOT SURE OF THAT
        onException(HttpUtils.STREAM_CLOSED_EXCEPTION);
      }
      super.onClose();
      // commented to be used later when we properly define the HTTP/2 connection expiration from the pool
      // boolean disposable = conn.streams.isEmpty();
      if (!push) {
        conn.recycle();
      } /* else {
        conn.listener.onRecycle(0, disposable);
      } */
      if (closeHandler != null) {
        closeHandler.handle(null);
      }
    }
  }

  private void recycle() {
    throw new RuntimeException("Method not implemented");
  }


  static class StreamImpl extends Stream implements HttpClientStream {

    StreamImpl(Http3ClientConnection conn, ContextInternal context, boolean push) {
      super(conn, context, push);
    }

    @Override
    public void closeHandler(Handler<Void> handler) {
      closeHandler = handler;
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
    public void unknownFrameHandler(Handler<HttpFrame> handler) {
      unknownFrameHandler = handler;
    }

    @Override
    public void pushHandler(Handler<HttpClientPush> handler) {
      pushHandler = handler;
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
      return this;
    }

    @Override
    public boolean writeQueueFull() {
      return !isNotWritable();
    }

    @Override
    public synchronized boolean isNotWritable() {
      return writeWindow > windowSize;
    }

    @Override
    public void headHandler(Handler<HttpResponseHead> handler) {
      headHandler = handler;
    }

    @Override
    public void chunkHandler(Handler<Buffer> handler) {
      chunkHandler = handler;
    }

    @Override
    public void priorityHandler(Handler<StreamPriority> handler) {
      priorityHandler = handler;
    }

    @Override
    public void endHandler(Handler<MultiMap> handler) {
      endHandler = handler;
    }

    @Override
    public StreamPriority priority() {
      return super.priority();
    }

    @Override
    public void updatePriority(StreamPriority streamPriority) {
      super.updatePriority(streamPriority);
    }

    @Override
    public HttpVersion version() {
      return HttpVersion.HTTP_2;
    }

    @Override
    void handleEnd(MultiMap trailers) {
      if (endHandler != null) {
        endHandler.handle(trailers);
      }
    }

    @Override
    void handleData(Buffer buf) {
      if (chunkHandler != null) {
        chunkHandler.handle(buf);
      }
    }

    @Override
    void handleReset(long errorCode) {
      handleException(new StreamResetException(errorCode));
    }

    @Override
    void handleWritabilityChanged(boolean writable) {
    }

    @Override
    void handleCustomFrame(HttpFrame frame) {
      if (unknownFrameHandler != null) {
        unknownFrameHandler.handle(frame);
      }
    }


    @Override
    void handlePriorityChange(StreamPriority streamPriority) {
      if (priorityHandler != null) {
        priorityHandler.handle(streamPriority);
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

    void handleException(Throwable exception) {
      if (exceptionHandler != null) {
        exceptionHandler.handle(exception);
      }
    }

    @Override
    public void writeHead(HttpRequestHead request, boolean chunked, ByteBuf buf, boolean end, StreamPriority priority, boolean connect, Handler<AsyncResult<Void>> handler) {
      priority(priority);
      conn.context.emit(null, v -> {
        writeHeaders(request, buf, end, priority, connect, handler);
      });
    }

    private void writeHeaders(HttpRequestHead request, ByteBuf buf, boolean end, StreamPriority priority, boolean connect, Handler<AsyncResult<Void>> handler) {
      Http3Headers headers = new DefaultHttp3Headers();
      headers.method(request.method.name());
      boolean e;
      if (request.method == HttpMethod.CONNECT) {
        if (request.authority == null) {
          throw new IllegalArgumentException("Missing :authority / host header");
        }
        headers.authority(request.authority);
        // don't end stream for CONNECT
        e = false;
      } else {
        headers.path(request.uri);
        headers.scheme(conn.isSsl() ? "https" : "http");
        if (request.authority != null) {
          headers.authority(request.authority);
        }
        e= end;
      }
      if (request.headers != null && request.headers.size() > 0) {
        for (Map.Entry<String, String> header : request.headers) {
          headers.add(HttpUtils.toLowerCase(header.getKey()), header.getValue());
        }
      }
      if (conn.client.options().isTryUseCompression() && headers.get(HttpHeaderNames.ACCEPT_ENCODING) == null) {
        headers.set(HttpHeaderNames.ACCEPT_ENCODING, Http1xClientConnection.determineCompressionAcceptEncoding());
      }
      try {//TODO: review here.
        createStream(request, headers, handler, event -> {
          if (buf != null) {
            doWriteHeaders(headers, false, false, null);
            doWriteData(buf, e, handler);
          } else {
            doWriteHeaders(headers, e, true, handler);
          }
        });
      } catch (Http2Exception ex) {
        if (handler != null) {
          handler.handle(context.failedFuture(ex));
        }
        handleException(ex);
        return;
      }
    }

    private void createStream(HttpRequestHead head, Http3Headers headers, Handler<AsyncResult<Void>> handler,
                              Handler<AsyncResult<Void>> onComplete) throws Http2Exception {
//      int id = this.conn.handler.encoder().connection().local().lastStreamCreated();
      int id = this.conn.quicStreamChannel!=null? (int) this.conn.quicStreamChannel.streamId() : 0;//TODO: review this!

      if (id == 0) {
        id = 1;
      } else {
        id += 2;
      }
      head.id = id;
      head.remoteAddress = conn.remoteAddress();
//      VertxHttp3Stream stream = this.conn.handler.encoder().connection().local().createStream(id, false);

      Http3.newRequestStream(conn.quicChannel, new Http3RequestStreamInboundHandler() {
          @Override
          protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) throws Exception {
            System.err.print(frame.content().toString(CharsetUtil.US_ASCII));
          }

          @Override
          protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) throws Exception {
//            System.err.print(frame.headers());
          }

          @Override
          protected void channelInputClosed(ChannelHandlerContext ctx) throws Exception {
            ctx.close();
          }
        })
        .addListener(new GenericFutureListener<io.netty.util.concurrent.Future<QuicStreamChannel>>() {
        @Override
        public void operationComplete(io.netty.util.concurrent.Future<QuicStreamChannel> quicStreamChannelFuture) throws Exception {
          QuicStreamChannel quicStreamChannel = quicStreamChannelFuture.get();

          init(quicStreamChannel);



          if (conn.metrics != null) {
            metric = conn.metrics.requestBegin(headers.path().toString(), head);
          }
          VertxTracer tracer = context.tracer();
          if (tracer != null) {
            BiConsumer<String, String> headers_ = (key, val) -> new Http3HeadersAdaptor(headers).add(key, val);
            String operation = head.traceOperation;
            if (operation == null) {
              operation = headers.method().toString();
            }
            trace = tracer.sendRequest(context, SpanKind.RPC, conn.client.options().getTracingPolicy(), head, operation, headers_, HttpUtils.CLIENT_HTTP_REQUEST_TAG_EXTRACTOR);
          }

          onComplete.handle(Future.succeededFuture());

        }
      });
    }

    @Override
    public void writeBuffer(ByteBuf buf, boolean end, Handler<AsyncResult<Void>> listener) {
      if (buf != null) {
        int size = buf.readableBytes();
        synchronized (this) {
          writeWindow += size;
        }
        if (listener != null) {
          Handler<AsyncResult<Void>> prev = listener;
          listener = ar -> {
            Handler<Void> drainHandler;
            synchronized (this) {
              boolean full = writeWindow > windowSize;
              writeWindow -= size;
              if (full && writeWindow <= windowSize) {
                drainHandler = this.drainHandler;
              } else {
                drainHandler = null;
              }
            }
            if (drainHandler != null) {
              drainHandler.handle(null);
            }
            prev.handle(ar);
          };
        }
      }
      writeData(buf, end, listener);
    }

    @Override
    public ContextInternal getContext() {
      return context;
    }

    @Override
    public void doSetWriteQueueMaxSize(int size) {
    }

    @Override
    public void reset(Throwable cause) {
      long code = cause instanceof StreamResetException ? ((StreamResetException)cause).getCode() : 0;
      conn.context.emit(code, this::writeReset);
    }

    @Override
    public HttpClientConnection connection() {
      return conn;
    }
  }





  private void metricsEnd(Stream stream) {
    throw new RuntimeException("Method not implemented");
  }

}
