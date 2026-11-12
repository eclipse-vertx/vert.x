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

import io.vertx.core.internal.http.ContentEncoder;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.*;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.http.HttpClientRequestInternal;
import io.vertx.core.internal.streams.FailedWrite;
import io.vertx.core.internal.streams.WriteResult;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.util.ArrayList;
import java.util.List;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.util.ReferenceCountUtil;
import io.netty.buffer.CompositeByteBuf;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static io.vertx.core.http.HttpHeaders.*;
import static io.vertx.core.http.impl.HttpClientImpl.ABS_URI_START_PATTERN;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientRequestImpl extends HttpClientRequestBase implements HttpClientRequest, ContentEncoder {

  static final Logger log = LoggerFactory.getLogger(HttpClientRequestImpl.class);
  private final Promise<Void> endPromise;
  private final Future<Void> endFuture;
  private boolean chunked;
  private Handler<Void> continueHandler;
  private Handler<MultiMap> earlyHintsHandler;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Function<HttpClientResponse, Future<HttpClientRequest>> redirectHandler;
  private boolean followRedirects;
  private int maxRedirects;
  private int numberOfRedirections;
  private final MultiMap headers;
  private MultiMap trailers;
  private boolean trailersSent;
  private boolean headersSent;
  private boolean isConnect;
  private String traceOperation;
  private int maxRedirectBufferSize = HttpClientOptions.DEFAULT_MAX_REDIRECT_BUFFERED_SIZE;
  private List<Buffer> bodyBuffer;
  public HttpClientRequestImpl(HostAndPort authority, HttpConnection connection, HttpClientStream stream, boolean validateUri) {
    super(authority, connection, stream, stream.context().promise(), HttpMethod.GET, "/", validateUri);
    this.chunked = false;
    this.endPromise = context.promise();
    this.endFuture = endPromise.future();
    this.numberOfRedirections = 0;
    this.headers = stream.connection().newHttpRequestHeaders();

    //
    stream.continueHandler(this::handleContinue);
    stream.earlyHintsHandler(this::handleEarlyHints);
    stream.drainHandler(this::handleDrained);
    stream.exceptionHandler(this::handleException);
    stream.resetHandler(this::handleReset);
  }

  public void init(RequestOptions options) {
    MultiMap headers = options.getHeaders();
    if (headers != null) {
      headers().setAll(headers);
    }
    HttpClientConnection conn = stream.connection();
    boolean useSSL = conn.isSsl();
    String requestURI = options.getURI();
    HttpMethod method = options.getMethod();
    String traceOperation = options.getTraceOperation();
    Boolean followRedirects = options.getFollowRedirects();
    long idleTimeout = options.getIdleTimeout();
    ProxyOptions proxyOptions = options.getProxyOptions();
    if (proxyOptions != null && !useSSL && (proxyOptions.getType() == ProxyType.HTTP || proxyOptions.getType() == ProxyType.HTTPS)) {
      HostAndPort authority = conn.authority();
      if (!ABS_URI_START_PATTERN.matcher(requestURI).find()) {
        int defaultPort = 80;
        String addPort = (authority.port() != -1 && authority.port() != defaultPort) ? (":" + authority.port()) : "";
        requestURI = "http://" + authority.host() + addPort + requestURI;
      }
      if (proxyOptions.getProxyAuthorization() != null) {
        headers().add(PROXY_AUTHORIZATION, proxyOptions.getProxyAuthorization());
      } else if (proxyOptions.getUsername() != null && proxyOptions.getPassword() != null) {
        headers().add(PROXY_AUTHORIZATION, "Basic " + Base64.getEncoder()
          .encodeToString((proxyOptions.getUsername() + ":" + proxyOptions.getPassword()).getBytes()));
      }
    }
    setURI(requestURI);
    setMethod(method);
    traceOperation(traceOperation);
    setFollowRedirects(followRedirects == Boolean.TRUE);
    if (idleTimeout > 0L) {
      // Maybe later ?
      idleTimeout(idleTimeout);
    }
  }

  void handleReset(long code) {
    handleException(new StreamResetException(code));
  }

  @Override
  void handleException(Throwable t) {
    t = mapException(t);
    super.handleException(t);
    if (endPromise.tryFail(t)) {
      Handler<Throwable> handler = exceptionHandler();
      if (handler != null) {
        context.emit(t, handler);
      } else {
        if (log.isDebugEnabled()) {
          log.error(t.getMessage(), t);
        } else {
          log.error(t.getMessage());
        }
      }
    }
  }

  @Override
  public synchronized HttpClientRequest setFollowRedirects(boolean followRedirects) {
    checkEnded();
    this.followRedirects = followRedirects;
    return this;
  }

  @Override
  public synchronized boolean isFollowRedirects() {
    return followRedirects;
  }

  @Override
  public synchronized HttpClientRequest setMaxRedirects(int maxRedirects) {
    Arguments.require(maxRedirects >= 0, "Max redirects must be >= 0");
    checkEnded();
    this.maxRedirects = maxRedirects;
    return this;
  }

  @Override
  public synchronized int getMaxRedirects() {
    return maxRedirects;
  }

  @Override
  public synchronized HttpClientRequest maxRedirectBufferSize(int maxRedirectBufferSize) {
    Arguments.require(maxRedirectBufferSize >= 0, "Max redirect buffer size must be >= 0");
    this.maxRedirectBufferSize = maxRedirectBufferSize;
    return this;
  }

  @Override
  public synchronized int maxRedirectBufferSize() {
    return maxRedirectBufferSize;
  }

  @Override
  public int numberOfRedirections() {
    return numberOfRedirections;
  }

  @Override
  public synchronized HttpClientRequestImpl setChunked(boolean chunked) {
    checkEnded();
    if (headersSent) {
      throw new IllegalStateException("Cannot set chunked after data has been written on request");
    }
    // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
    if (version() != io.vertx.core.http.HttpVersion.HTTP_1_0) {
      this.chunked = chunked;
    }
    return this;
  }

  @Override
  public synchronized boolean isChunked() {
    return chunked;
  }

  @Override
  public MultiMap headers() {
    return headers;
  }

  @Override
  public synchronized HttpClientRequest putHeader(String name, String value) {
    checkEnded();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putHeader(String name, Iterable<String> values) {
    checkEnded();
    headers().set(name, values);
    return this;
  }

  @Override
  public synchronized MultiMap trailers() {
    if (trailers == null) {
      if (getMethod() == HttpMethod.CONNECT) {
        throw new IllegalStateException("A CONNECT request cannot send trailers");
      }
      trailers = stream.connection().newHttpRequestHeaders();
    }
    return trailers;
  }

  @Override
  public synchronized HttpClientRequest putTrailer(String name, String value) {
    checkEnded();
    trailers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putTrailer(CharSequence name, CharSequence value) {
    checkEnded();
    trailers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putTrailer(String name, Iterable<String> values) {
    checkEnded();
    trailers().set(name, values);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putTrailer(CharSequence name, Iterable<CharSequence> values) {
    checkEnded();
    trailers().set(name, values);
    return this;
  }

  @Override
  public synchronized HttpClientRequestInternal setWriteQueueMaxSize(int maxSize) {
    checkEnded();
    stream.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (this) {
      checkEnded();
    }
    return !stream.isWritable();
  }

  @Override
  public HttpVersion version() {
    return stream.version();
  }

  private synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  public synchronized HttpClientRequestInternal exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      checkEnded();
      this.exceptionHandler = handler;
    } else {
      this.exceptionHandler = null;
    }
    return this;
  }

  @Override
  public synchronized HttpClientRequestInternal drainHandler(Handler<Void> handler) {
    if (handler != null) {
      checkEnded();
    }
    drainHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpClientRequest continueHandler(Handler<Void> handler) {
    if (handler != null) {
      checkEnded();
    }
    this.continueHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpClientRequest earlyHintsHandler(@Nullable Handler<MultiMap> handler) {
    if (handler != null) {
      checkEnded();
    }
    this.earlyHintsHandler = handler;
    return this;
  }

  @Override
  public synchronized HttpClientRequest redirectHandler(@Nullable Function<HttpClientResponse, Future<HttpClientRequest>> handler) {
    if (handler != null) {
      checkEnded();
    }
    this.redirectHandler = handler;
    return this;
  }

  @Override
  public Future<HttpClientResponse> send(ClientForm body) {
    ClientMultipartFormImpl impl = (ClientMultipartFormImpl) body;
    String contentType = headers != null ? headers.get(HttpHeaders.CONTENT_TYPE) : null;
    boolean multipartMixed = impl.mixed();
    HttpPostRequestEncoder.EncoderMode encoderMode = multipartMixed ? HttpPostRequestEncoder.EncoderMode.RFC1738 : HttpPostRequestEncoder.EncoderMode.HTML5;
    ClientMultipartFormUpload form;
    try {
      boolean multipart;
      if (contentType == null) {
        multipart = impl.isMultipart();
        contentType = multipart ? HttpHeaders.MULTIPART_FORM_DATA.toString() : HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString();
        putHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
      } else {
        if (contentType.equalsIgnoreCase(HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED.toString())) {
          if (impl.isMultipart()) {
            throw new IllegalStateException("Multipart form requires multipart/form-data content type instead of "
              + HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED);
          }
          multipart = false;
        } else if (contentType.equalsIgnoreCase(HttpHeaders.MULTIPART_FORM_DATA.toString())) {
          multipart = true;
        } else {
          throw new IllegalStateException("Sending form requires multipart/form-data or "
            + HttpHeaders.APPLICATION_X_WWW_FORM_URLENCODED + " content type instead of " + contentType);
        }
      }
      form = new ClientMultipartFormUpload(context, impl, multipart, encoderMode);
    } catch (Exception e) {
      reset(0, e);
      return response();
    }
    for (Map.Entry<String, String> header : form.headers()) {
      if (header.getKey().equalsIgnoreCase(CONTENT_LENGTH.toString())) {
        if (Integer.parseInt(header.getValue()) < 0) {
          // Bug ?
          continue;
        }
      }
      putHeader(header.getKey(), header.getValue());
    }
    return send(form);
  }

  @Override
  public Future<Void> sendHead() {
    checkEnded();
    return doWrite(null, false, false);
  }

  @Override
  public Future<HttpClientResponse> connect() {
    synchronized (this) {
      if (chunked || headers.contains(CONTENT_LENGTH) || headers.contains(TRANSFER_ENCODING)) {
        return context.failedFuture(new IllegalStateException("A tunnel request cannot declare content-length or transfer-encoding"));
      }
      if (trailers != null) {
        return context.failedFuture(new IllegalStateException("A tunnel request cannot send trailers"));
      }
    }
    doWrite(null, false, true);
    return response();
  }

  @Override
  public synchronized HttpClientRequest putHeader(CharSequence name, CharSequence value) {
    checkEnded();
    headers().set(name, value);
    return this;
  }

  @Override
  public synchronized HttpClientRequest putHeader(CharSequence name, Iterable<CharSequence> values) {
    checkEnded();
    headers().set(name, values);
    return this;
  }

  @Override
  public synchronized HttpClientRequest traceOperation(String op) {
    checkEnded();
    traceOperation = op;
    return this;
  }

  @Override
  public String traceOperation() {
    return traceOperation;
  }

  private void tryComplete() {
    endPromise.tryComplete();
  }

  @Override
  public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) {
    synchronized (this) {
      checkEnded();
    }
    return stream.writeFrame(type, flags, payload);
  }

  private void handleDrained(Void v) {
    Handler<Void> handler;
    synchronized (this) {
      handler =  drainHandler;
      if (handler == null || endFuture.isComplete()) {
        return;
      }
    }
    context.dispatch(handler);
  }

  private void handleNextRequest(HttpClientRequest next, Promise<HttpClientResponse> handler, long timeoutMs) {
    next.response().onComplete(handler);
    next.exceptionHandler(exceptionHandler());
    exceptionHandler(null);
    next.pushHandler(pushHandler());
    next.setFollowRedirects(true);
    next.setMaxRedirects(maxRedirects);
    HttpClientRequestImpl nextImpl = (HttpClientRequestImpl) next;
    nextImpl.numberOfRedirections = numberOfRedirections + 1;
    nextImpl.bodyBuffer = bodyBuffer;
    endFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        if (timeoutMs > 0) {
          next.idleTimeout(timeoutMs);
        }
        if (next.getMethod() == HttpMethod.QUERY && bodyBuffer != null && !bodyBuffer.isEmpty()) {
          Buffer redirectBody;
          if (bodyBuffer.size() == 1) {
            redirectBody = bodyBuffer.get(0);
          } else {
            CompositeByteBuf composite = Unpooled.compositeBuffer();
            for (Buffer b : bodyBuffer) {
              composite.addComponent(true, ((BufferInternal) b).getByteBuf());
            }
            redirectBody = BufferInternal.buffer(composite);
          }
          next.end(redirectBody);
        } else {
          next.end();
        }
      } else {
        next.reset(0);
      }
    });
  }

  private void handleContinue(Void v) {
    Handler<Void> handler;
    synchronized (this) {
      handler = continueHandler;
    }
    if (handler != null) {
      context.dispatch(null, handler);
    }
  }

  private void handleEarlyHints(MultiMap headers) {
    Handler<MultiMap> handler;
    synchronized (this) {
      handler = earlyHintsHandler;
    }
    if (handler != null) {
      handler.handle(headers);
    }
  }

  void handleResponse(Promise<HttpClientResponse> promise, HttpClientResponse resp, long timeoutMs) {
    int statusCode = resp.statusCode();
    if (followRedirects && numberOfRedirections < maxRedirects && statusCode >= 300 && statusCode < 400) {
      Function<HttpClientResponse, Future<HttpClientRequest>> handler = redirectHandler;
      if (handler != null) {
        ContextInternal prev = context.beginDispatch();
        Future<HttpClientRequest> next;
        try {
          next = handler.apply(resp);
        } finally {
          context.endDispatch(prev);
        }
        if (next != null) {
          resp
            .end()
            .compose(v -> next, err -> next)
            .onComplete(ar1 -> {
              if (ar1.succeeded()) {
                handleNextRequest(ar1.result(), promise, timeoutMs);
              } else {
                fail(ar1.cause());
              }
            });
          return;
        }
      }
    }
    promise.complete(resp);
  }

  @Override
  public Future<Void> end(String chunk) {
    return write(BufferInternal.buffer(chunk).getByteBuf(), true);
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    return write(BufferInternal.buffer(chunk, enc).getByteBuf(), true);
  }

  @Override
  public WriteResult end(Buffer chunk) {
    if (chunk == null) {
      throw new NullPointerException("no null chunk accepted");
    }
    return write(((BufferInternal) chunk).getByteBuf(), true);
  }

  @Override
  public WriteResult end() {
    return write(null, true);
  }

  @Override
  public WriteResult write(Buffer chunk) {
    if (chunk == null) {
      throw new NullPointerException("no null chunk accepted");
    }
    return write(((BufferInternal) chunk).getByteBuf(), false);
  }

  @Override
  public Future<Void> write(String chunk) {
    return write(BufferInternal.buffer(chunk).getByteBuf(), false);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    Objects.requireNonNull(enc, "no null encoding accepted");
    return write(BufferInternal.buffer(chunk, enc).getByteBuf(), false);
  }

  @Override
  public ContentEncoder contentEncoder() {
    return this;
  }

  @Override
  public WriteResult write(ByteBuf chunk) {
    return write(chunk, false);
  }

  @Override
  public WriteResult end(ByteBuf chunk) {
    return write(chunk, true);
  }

  private WriteResult write(ByteBuf buff, boolean end) {
    return doWrite(buff, end, false);
  }

  private WriteResult doWrite(ByteBuf buff, boolean end, boolean connect) {
    boolean written = false;
    try {
      boolean writeHead;
      boolean writeEnd;
      MultiMap trailers;
      Throwable failure;
      synchronized (this) {
        if (reset != null) {
          return new FailedWrite(context, reset);
        }
        if (trailersSent) {
          return new FailedWrite(context, new IllegalStateException("Request already complete"));
        }
        if (followRedirects && getMethod() == HttpMethod.QUERY) {
          if (buff != null) {
            int currentLen = 0;
            if (bodyBuffer != null) {
              for (Buffer b : bodyBuffer) {
                currentLen += b.length();
              }
            }
            if (currentLen + buff.readableBytes() > maxRedirectBufferSize) {
              followRedirects = false;
              bodyBuffer = null;
            } else {
              if (bodyBuffer == null) {
                bodyBuffer = new ArrayList<>();
              }
              bodyBuffer.add(Buffer.buffer(ByteBufUtil.getBytes(buff)));
            }
          }
        }
        trailers = this.trailers;
        failure = end && trailers != null ? checkTrailers(trailers) : null;
        if (failure == null && !headersSent) {
          if (!connect) {
            if (trailers != null && version() == HttpVersion.HTTP_1_1 && !headers.contains(CONTENT_LENGTH)) {
              chunked = true;
            }
            boolean requiresContentLength = !this.chunked && !headers.contains(CONTENT_LENGTH);
            if (end) {
              if (buff != null && requiresContentLength) {
                headers().set(CONTENT_LENGTH, HttpUtils.positiveLongToString(buff.readableBytes()));
              }
            } else if (requiresContentLength) {
              chunked = true;
            }
            if (chunked) {
              headers.set(TRANSFER_ENCODING, CHUNKED);
            }
          }
          headersSent = true;
          isConnect = connect;
          writeHead = true;
        } else {
          writeHead = false;
        }
        writeEnd = !isConnect && end;
        trailersSent = end;
      }

      if (failure != null) {
        reset(0, failure);
        return new FailedWrite(context, failure);
      }
      if (writeHead) {
        stream.head(head());
      }
      ContentEncoder encoder = stream.contentEncoder();
      WriteResult future;
      if (writeEnd && trailers != null) {
        if (buff != null) {
          encoder.write(buff);
          written = true;
        }
        future = stream.end(trailers);
      } else if (writeEnd) {
        future = buff != null ? encoder.end(buff) : encoder.end();
        written = true;
      } else if (buff != null) {
        future = encoder.write(buff);
        written = true;
      } else if (writeHead) {
        future = encoder.write(Unpooled.EMPTY_BUFFER);
      } else if (!end) {
        future = new FailedWrite(context, "Head already sent");
      } else {
        future = new FailedWrite(context, "A CONNECT request cannot be ended");
      }
      if (end) {
        tryComplete();
      }
      return future;
    } finally {
      if (!written) {
        ReferenceCountUtil.release(buff);
      }
    }
  }

  private HttpRequestHead head() {
    HttpMethod method = getMethod();
    String uri = getURI();
    if (uri.isEmpty()) {
      uri = "/";
    }
    return new HttpRequestHead(ssl ? "https" : "http", method, uri, headers, authority(), traceOperation);
  }

  private Throwable checkTrailers(MultiMap trailers) {
    if (getMethod() == HttpMethod.CONNECT) {
      return new IllegalStateException("A CONNECT request cannot send trailers");
    }
    HttpVersion version = version();
    if (version == HttpVersion.HTTP_1_0) {
      return new IllegalStateException("HTTP/1.0 cannot send trailers");
    }
    if (version == HttpVersion.HTTP_1_1 && !(headersSent ? chunked : chunked || !headers.contains(CONTENT_LENGTH))) {
      return new IllegalStateException("HTTP/1.1 trailers require chunked encoding");
    }
    try {
      HttpUtils.validateTrailers(trailers);
    } catch (IllegalArgumentException e) {
      return e;
    }
    return null;
  }

  private void checkEnded() {
    if (trailersSent) {
      throw new IllegalStateException("Request already complete");
    }
  }

  @Override
  public synchronized HttpClientRequest setStreamPriority(StreamPriority priority) {
    stream.updatePriority(priority);
    return this;
  }

  @Override
  public synchronized StreamPriority getStreamPriority() {
    return stream.priority();
  }
}
