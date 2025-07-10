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
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HeadersMultiMap;
import io.vertx.core.impl.Arguments;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;

import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import static io.vertx.core.http.HttpHeaders.CONTENT_LENGTH;
import static io.vertx.core.http.impl.HttpClientImpl.ABS_URI_START_PATTERN;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpClientRequestImpl extends HttpClientRequestBase implements HttpClientRequest {

  static final Logger log = LoggerFactory.getLogger(HttpClientRequestImpl.class);

  private final Promise<Void> endPromise;
  private final Future<Void> endFuture;
  private boolean chunked;
  private Handler<Void> continueHandler;
  private Handler<MultiMap> earlyHintsHandler;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Function<HttpClientResponse, Future<HttpClientRequest>> redirectHandler;
  private boolean ended;
  private boolean followRedirects;
  private int maxRedirects;
  private int numberOfRedirections;
  private HeadersMultiMap headers;
  private StreamPriorityBase priority;
  private boolean headWritten;
  private boolean isConnect;
  private String traceOperation;

  HttpClientRequestImpl(HttpConnection connection, HttpClientStream stream) {
    super(connection, stream, stream.context().promise(), HttpMethod.GET, "/");
    this.chunked = false;
    this.endPromise = context.promise();
    this.endFuture = endPromise.future();
    this.priority = stream.createDefaultStreamPriority();
    this.numberOfRedirections = 0;

    //
    stream.continueHandler(this::handleContinue);
    stream.earlyHintsHandler(this::handleEarlyHints);
    stream.drainHandler(this::handleDrained);
    stream.exceptionHandler(this::handleException);
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
    if (proxyOptions != null && !useSSL && proxyOptions.getType() == ProxyType.HTTP) {
      HostAndPort authority = conn.authority();
      if (!ABS_URI_START_PATTERN.matcher(requestURI).find()) {
        int defaultPort = 80;
        String addPort = (authority.port() != -1 && authority.port() != defaultPort) ? (":" + authority.port()) : "";
        requestURI = "http://" + authority.host() + addPort + requestURI;
      }
      if (proxyOptions.getUsername() != null && proxyOptions.getPassword() != null) {
        headers().add("Proxy-Authorization", "Basic " + Base64.getEncoder()
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
  public int numberOfRedirections() {
    return numberOfRedirections;
  }

  @Override
  public synchronized HttpClientRequestImpl setChunked(boolean chunked) {
    checkEnded();
    if (headWritten) {
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
  public synchronized MultiMap headers() {
    if (headers == null) {
      headers = HeadersMultiMap.httpHeaders();
    }
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
  public synchronized HttpClientRequest setWriteQueueMaxSize(int maxSize) {
    checkEnded();
    stream.setWriteQueueMaxSize(maxSize);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (this) {
      checkEnded();
    }
    return stream.writeQueueFull();
  }

  @Override
  public HttpVersion version() {
    return stream.version();
  }

  private synchronized Handler<Throwable> exceptionHandler() {
    return exceptionHandler;
  }

  public synchronized HttpClientRequest exceptionHandler(Handler<Throwable> handler) {
    if (handler != null) {
      checkEnded();
      this.exceptionHandler = handler;
    } else {
      this.exceptionHandler = null;
    }
    return this;
  }

  @Override
  public synchronized HttpClientRequest drainHandler(Handler<Void> handler) {
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
    return stream.writeFrame(type, flags, ((BufferInternal)payload).getByteBuf());
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
    ((HttpClientRequestImpl)next).numberOfRedirections = numberOfRedirections + 1;
    endFuture.onComplete(ar -> {
      if (ar.succeeded()) {
        if (timeoutMs > 0) {
          next.idleTimeout(timeoutMs);
        }
        next.end();
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
  public Future<Void> end(Buffer chunk) {
    return write(((BufferInternal)chunk).getByteBuf(), true);
  }

  @Override
  public Future<Void> end() {
    return write(null, true);
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    ByteBuf buf = ((BufferInternal)chunk).getByteBuf();
    return write(buf, false);
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

  private boolean requiresContentLength() {
    return !chunked && (headers == null || !headers.contains(CONTENT_LENGTH)) && !isConnect;
  }

  private Future<Void> write(ByteBuf buff, boolean end) {
    if (end) {
      if (buff != null && requiresContentLength()) {
        headers().set(CONTENT_LENGTH, HttpUtils.positiveLongToString(buff.readableBytes()));
      }
    } else if (requiresContentLength()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
        + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }
    return doWrite(buff, end, false);
  }

  private Future<Void> doWrite(ByteBuf buff, boolean end, boolean connect) {
    boolean writeHead;
    boolean writeEnd;
    synchronized (this) {
      if (reset != null) {
        return context.failedFuture(reset);
      }
      if (ended) {
        return context.failedFuture(new IllegalStateException("Request already complete"));
      }
      checkResponseHandler();
      if (!headWritten) {
        headWritten = true;
        isConnect = connect;
        writeHead = true;
      } else {
        writeHead = false;
      }
      writeEnd = !isConnect && end;
      ended = end;
    }

    Future<Void> future;
    if (writeHead) {
      HttpMethod method = getMethod();
      String uri = getURI();
      if (uri.isEmpty()) {
        uri = "/";
      }
      HttpRequestHead head = new HttpRequestHead(method, uri, headers, authority(), absoluteURI(), traceOperation);
      future = stream.writeHead(head, chunked, buff, writeEnd, priority, connect);
    } else {
      if (buff == null && !end) {
        throw new IllegalArgumentException();
      }
      future = stream.write(buff, writeEnd);
    }
    if (end) {
      tryComplete();
    }
    return future;
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request already complete");
    }
  }

  private void checkResponseHandler() {
/*
    if (stream == null && !connecting && responsePromise.future().getHandler() == null) {
      throw new IllegalStateException("You must set a response handler before connecting to the server");
    }
*/
  }

  @Override
  public synchronized HttpClientRequest setStreamPriority(StreamPriorityBase priority) {
    if (headWritten) {
      stream.updatePriority(priority);
    } else {
      this.priority = priority;
    }
    return this;
  }

  @Override
  public synchronized StreamPriorityBase getStreamPriority() {
    return stream.priority();
  }
}
