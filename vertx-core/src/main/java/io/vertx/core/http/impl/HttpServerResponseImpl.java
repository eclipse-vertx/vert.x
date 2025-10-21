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
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpStatusClass;
import io.netty.handler.stream.ChunkedInput;
import io.netty.handler.stream.ChunkedNioFile;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.http.impl.headers.HttpResponseHeaders;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.impl.ConnectionBase;
import io.vertx.core.net.impl.UncloseableChunkedNioFile;
import io.vertx.core.spi.observability.HttpResponse;
import io.vertx.core.streams.ReadStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.Map.Entry;
import java.util.Set;

import static io.vertx.core.http.HttpHeaders.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpServerResponseImpl implements HttpServerResponse, HttpResponse {

  private final HttpServerStream stream;
  private final HttpServerConnection conn;
  private final ContextInternal context;
  private final boolean push;
  private final HttpResponseHeaders headersMap;
  private MultiMap trailedMap;
  private boolean chunked;
  private boolean headWritten;
  private boolean ended;
  private boolean closed;
  private CookieJar cookies;
  private HttpResponseStatus status = HttpResponseStatus.OK;
  private String statusMessage; // Not really used but we keep the message for the getStatusMessage()
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> headersEndHandler;
  private Handler<Void> bodyEndHandler;
  private Handler<Void> closeHandler;
  private Handler<Void> endHandler;
  private Future<NetSocket> netSocket;
  private HttpMethod requestMethod;
  private HostAndPort requestAuthority;
  private CharSequence requestCookie;

  public HttpServerResponseImpl(HttpServerStream stream,
                                ContextInternal context,
                                boolean push) {
    this.stream = stream;
    this.context = context;
    this.conn = stream.connection();
    this.push = push;
    this.headersMap = new HttpResponseHeaders(conn.newHeaders());
  }

  void init(HttpRequestHead head) {
    this.requestMethod = head.method();
    this.requestAuthority = head.authority;
    this.requestCookie = head.headers().get(io.vertx.core.http.HttpHeaders.COOKIE);
  }

  public boolean isPush() {
    return push;
  }

  void handleReset(long code) {
    handleException(new StreamResetException(code));
  }

  void handleException(Throwable cause) {
    Handler<Throwable> handler;
    synchronized (conn) {
      if (ended) {
        return;
      }
      handler = exceptionHandler;
    }
    if (handler != null) {
      handler.handle(cause);
    }
  }

  void handleClose(Void v) {
    Handler<Void> endHandler;
    Handler<Void> closeHandler;
    synchronized (conn) {
      closed = true;
      boolean failed = !ended;
      endHandler = failed ? this.endHandler : null;
      closeHandler = this.closeHandler;
    }
    if (endHandler != null) {
      endHandler.handle(null);
    }
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  private void checkHeadWritten() {
    if (headWritten) {
      throw new IllegalStateException("Response head already sent");
    }
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      exceptionHandler = handler;
      return this;
    }
  }

  @Override
  public int statusCode() {
    return getStatusCode();
  }

  @Override
  public int getStatusCode() {
    synchronized (conn) {
      return status.code();
    }
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    if (statusCode < 0) {
      throw new IllegalArgumentException("code: " + statusCode + " (expected: 0+)");
    }
    synchronized (conn) {
      checkHeadWritten();
      this.status = HttpResponseStatus.valueOf(statusCode);
      return this;
    }
  }

  @Override
  public String getStatusMessage() {
    synchronized (conn) {
      if (statusMessage == null) {
        return status.reasonPhrase();
      }
      return statusMessage;
    }
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    synchronized (conn) {
      checkHeadWritten();
      this.statusMessage = statusMessage;
      return this;
    }
  }

  @Override
  public HttpServerResponse setChunked(boolean chunked) {
    synchronized (conn) {
      checkHeadWritten();
      this.chunked = true;
      return this;
    }
  }

  @Override
  public boolean isChunked() {
    synchronized (conn) {
      return chunked;
    }
  }

  @Override
  public MultiMap headers() {
    return headersMap;
  }

  @Override
  public HttpServerResponse putHeader(String name, String value) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(String name, Iterable<String> values) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    synchronized (conn) {
      checkHeadWritten();
      headers().set(name, values);
      return this;
    }
  }

  @Override
  public MultiMap trailers() {
    MultiMap ret = trailedMap;
    if (ret == null) {
      ret = new HttpHeaders(conn.newHeaders());
      trailedMap = ret;
    }
    return ret;
  }

  @Override
  public HttpServerResponse putTrailer(String name, String value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(String name, Iterable<String> values) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, values);
      return this;
    }
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    synchronized (conn) {
      checkValid();
      trailers().set(name, value);
      return this;
    }
  }

  @Override
  public HttpServerResponse closeHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      closeHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse endHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      endHandler = handler;
      return this;
    }
  }

  @Override
  public Future<Void> writeContinue() {
    synchronized (conn) {
      checkHeadWritten();
    }
    return stream.writeHead(new HttpResponseHead(HttpResponseStatus.CONTINUE.code(), HttpResponseStatus.CONTINUE.reasonPhrase(), new HttpResponseHeaders(conn.newHeaders())), null, false);
  }

  @Override
  public Future<Void> writeHead() {
    synchronized (conn) {
      checkHeadWritten();
    }
    return checkSendHeaders();
  }

  @Override
  public Future<Void> writeEarlyHints(MultiMap headers) {
    HttpResponseHeaders http2Headers = new HttpResponseHeaders(conn.newHeaders());
    for (Entry<String, String> header : headers) {
      http2Headers.add(header.getKey(), header.getValue());
    }
    synchronized (conn) {
      checkHeadWritten();
    }
    return stream.writeHead(new HttpResponseHead(HttpResponseStatus.EARLY_HINTS.code(), HttpResponseStatus.EARLY_HINTS.reasonPhrase(), http2Headers), null, false);
  }

  @Override
  public Future<Void> write(Buffer chunk) {
    return write(chunk, false);
  }

  @Override
  public Future<Void> write(String chunk, String enc) {
    return write(Buffer.buffer(chunk, enc), false);
  }

  @Override
  public Future<Void> write(String chunk) {
    return write(Buffer.buffer(chunk), false);
  }

  @Override
  public Future<Void> end(String chunk) {
    return end(Buffer.buffer(chunk));
  }

  @Override
  public Future<Void> end(String chunk, String enc) {
    return end(Buffer.buffer(chunk, enc));
  }

  @Override
  public Future<Void> end(Buffer chunk) {
    return write(chunk, true);
  }

  @Override
  public Future<Void> end() {
    return write(null, true);
  }

  Future<NetSocket> netSocket(ReadStream<Buffer> inbound) {
    synchronized (conn) {
      if (netSocket == null) {
        status = HttpResponseStatus.OK;
        if (checkSendHeaders() == null) {
          netSocket = context.failedFuture("Response for CONNECT already sent");
        } else {
          HttpNetSocket ns = HttpNetSocket.netSocket((ConnectionBase) conn, context, inbound, this);
          netSocket = Future.succeededFuture(ns);
        }
      }
    }
    return netSocket;
  }

  private Future<Void> write(Buffer chunk, boolean end) {
    Future<Void> future = write_(chunk, end && trailedMap == null);
    if (end) {
      if (trailedMap != null) {
        future = stream.writeHeaders(trailedMap, true);
      }
      Handler<Void> bodyEndHandler = this.bodyEndHandler;
      Handler<Void> endHandler = this.endHandler;
      if (bodyEndHandler != null) {
        bodyEndHandler.handle(null);
      }
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
    return future;
  }

  private Future<Void> write_(Buffer chunk, boolean end) {
    boolean sendHeaders;
    synchronized (conn) {
      if (ended) {
        throw new IllegalStateException("Response has already been written");
      }
      ended = end;
      if (end && !headWritten && requiresContentLengthHeader()) {
        headers().set(HttpHeaderNames.CONTENT_LENGTH, chunk == null ? "0" : HttpUtils.positiveLongToString(chunk.length()));
      }
      sendHeaders = prepareHeaders();
    }
    if (sendHeaders) {
      return stream.writeHead(new HttpResponseHead(status.code(), status.reasonPhrase(), headersMap), chunk, end);
    } else {
      return stream.writeChunk(chunk, end);
    }
  }

  private boolean requiresContentLengthHeader() {
    return requestMethod != HttpMethod.HEAD && status != HttpResponseStatus.NOT_MODIFIED && !headersMap.contains(HttpHeaderNames.CONTENT_LENGTH);
  }

  private Future<Void> checkSendHeaders() {
    if (prepareHeaders()) {
      return stream.writeHead(new HttpResponseHead(status.code(), status.reasonPhrase(), headersMap), null, false);
    } else {
      return null;
    }
  }

  private boolean prepareHeaders() {
    if (!headWritten) {
      if (headersEndHandler != null) {
        headersEndHandler.handle(null);
      }
      if (cookies != null) {
        setCookies();
      }
      // Sanitize
      if (requestMethod == HttpMethod.HEAD || status == HttpResponseStatus.NOT_MODIFIED) {
        headersMap.remove(io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING);
      } else if (status == HttpResponseStatus.RESET_CONTENT) {
        headersMap.remove(io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING);
        headersMap.set(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH, "0");
      } else if (status.codeClass() == HttpStatusClass.INFORMATIONAL || status == HttpResponseStatus.NO_CONTENT) {
        headersMap.remove(io.vertx.core.http.HttpHeaders.TRANSFER_ENCODING);
        headersMap.remove(io.vertx.core.http.HttpHeaders.CONTENT_LENGTH);
      }
      headWritten = true;
      return true;
    } else {
      return false;
    }
  }

  private void setCookies() {
    for (ServerCookie cookie: cookies) {
      if (cookie.isChanged()) {
        headersMap.add(SET_COOKIE, cookie.encode());
      }
    }
  }

  @Override
  public Future<Void> writeCustomFrame(int type, int flags, Buffer payload) {
    synchronized (conn) {
      checkValid();
      checkSendHeaders();
    }
    return stream.writeFrame(type, flags, payload);
  }

  private void checkValid() {
    if (ended) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  void handleWriteQueueDrained(Void v) {
    Handler<Void> handler;
    synchronized (conn) {
      handler = drainHandler;
      if (ended || handler == null) {
        return;
      }
    }
    handler.handle(null);
  }

  @Override
  public boolean writeQueueFull() {
    synchronized (conn) {
      checkValid();
      return !stream.isWritable();
    }
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int maxSize) {
    synchronized (conn) {
      checkValid();
      // It does not seem to be possible to configure this at the moment
    }
    return this;
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    synchronized (conn) {
      if (handler != null) {
        checkValid();
      }
      drainHandler = handler;
      return this;
    }
  }

  @Override
  public Future<Void> sendFile(String filename, long offset, long length) {
    if (offset < 0) {
      return context.failedFuture("offset : " + offset + " (expected: >= 0)");
    }
    if (length < 0) {
      return context.failedFuture("length : " + length + " (expected: >= 0)");
    }
    synchronized (conn) {
      checkValid();
    }
    if (conn.supportsSendFile()) {
      return sendFileInternal(filename, offset, length);
    } else {
      return sendAsyncFile(filename, offset, length);
    }
  }

  @Override
  public Future<Void> sendFile(RandomAccessFile file, long offset, long length) {
    if (!headersMap.contains(io.vertx.core.http.HttpHeaders.CONTENT_TYPE)) {
      headersMap.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
    }
    long size;
    try {
      size = file.length();
    } catch (IOException e) {
      return context.failedFuture(e);
    }
    return sendFileInternal(offset, length, size, file, null, false);
  }

  @Override
  public Future<Void> sendFile(FileChannel channel, long offset, long length) {
    if (!headersMap.contains(io.vertx.core.http.HttpHeaders.CONTENT_TYPE)) {
      headersMap.set(CONTENT_TYPE, APPLICATION_OCTET_STREAM);
    }
    long size;
    try {
      size = channel.size();
    } catch (IOException e) {
      return context.failedFuture(e);
    }
    return sendFileInternal(offset, length, size, null, channel, false);
  }

  private Future<Void> sendAsyncFile(String filename, long offset, long length) {
    return HttpUtils
      .resolveFile(context, filename, offset, length)
      .compose(file -> {
        long fileLength = file.getReadLength();
        long contentLength = Math.min(length, fileLength);
        // fail early before status code/headers are written to the response
        if (headersMap.get(HttpHeaderNames.CONTENT_LENGTH) == null) {
          putHeader(HttpHeaderNames.CONTENT_LENGTH, HttpUtils.positiveLongToString(contentLength));
        }
        if (headersMap.get(HttpHeaderNames.CONTENT_TYPE) == null) {
          String contentType = MimeMapping.mimeTypeForFilename(filename);
          if (contentType != null) {
            putHeader(HttpHeaderNames.CONTENT_TYPE, contentType);
          }
        }
        checkSendHeaders();
        Future<Void> fut = file.pipeTo(this);
        return fut
          .eventually(file::close);
      });
  }

  private Future<Void> sendFileInternal(String filename, long offset, long length) {
    File file = context.owner().fileResolver().resolve(filename);
    long size;
    RandomAccessFile raf;
    try {
      raf = new RandomAccessFile(file, "r");
      size = raf.length();
    } catch (Exception e) {
      return context.failedFuture(e);
    }
    if (!headersMap.contains(io.vertx.core.http.HttpHeaders.CONTENT_TYPE)) {
      CharSequence mimeType = MimeMapping.mimeTypeForFilename(filename);
      if (mimeType == null) {
        mimeType = APPLICATION_OCTET_STREAM;
      }
      headersMap.set(CONTENT_TYPE, mimeType);
    }
    return sendFileInternal(offset, length, size, raf, null, true);
  }

  private Future<Void> sendFileInternal(long offset, long length, long size, RandomAccessFile file, FileChannel channel, boolean close) {
    Future<Void> fut = null;
    try {
      long actualLength = Math.min(length, size - offset);
      long actualOffset = Math.min(offset, size);
      if (actualLength < 0) {
        return context.failedFuture("offset : " + offset + " is larger than the requested file length : " + size);
      }
      ChunkedInput<ByteBuf> chunkedFile;
      try {
        if (file != null) {
          channel = file.getChannel();
        }
        if (close) {
          chunkedFile = new ChunkedNioFile(channel, actualOffset, actualLength, 8192);
        } else {
          chunkedFile = new UncloseableChunkedNioFile(channel, actualOffset, actualLength);
        }
      } catch (IOException e) {
        return context.failedFuture(e);
      }
      fut = sendFileInternal(chunkedFile);
    } finally {
      if (fut == null && close) {
        try {
          if (file != null) {
            file.close();
          } else {
            channel.close();
          }
        } catch (Exception ignore) {
        }
      }
    }
    return fut;
  }

  private Future<Void> sendFileInternal(ChunkedInput<ByteBuf> file) {
    if (requiresContentLengthHeader()) {
      if (headersMap.get(HttpHeaderNames.CONTENT_LENGTH) == null) {
        putHeader(HttpHeaderNames.CONTENT_LENGTH, HttpUtils.positiveLongToString(file.length()));
      }
    }
    checkSendHeaders();
    Promise<Void> promise = context.promise();
    ended = true;
    stream.sendFile(file, promise);
    Future<Void> future = promise.future();
    Handler<Void> bodyEndHandler = this.bodyEndHandler;
    if (bodyEndHandler != null) {
      future.onSuccess(bodyEndHandler);
    }
    Handler<Void> endHandler = this.endHandler;
    if (endHandler != null) {
      future.onSuccess(endHandler);
    }

    return future;
  }

  @Override
  public boolean ended() {
    synchronized (conn) {
      return ended;
    }
  }

  @Override
  public synchronized boolean closed() {
    synchronized (conn) {
      return closed;
    }
  }

  @Override
  public boolean headWritten() {
    synchronized (conn) {
      return headWritten;
    }
  }

  @Override
  public HttpServerResponse headersEndHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      headersEndHandler = handler;
      return this;
    }
  }

  @Override
  public HttpServerResponse bodyEndHandler(@Nullable Handler<Void> handler) {
    synchronized (conn) {
      bodyEndHandler = handler;
      return this;
    }
  }

  @Override
  public long bytesWritten() {
    return stream.bytesWritten();
  }

  @Override
  public int streamId() {
    return stream.id();
  }

  @Override
  public Future<Void> reset(long code) {
    return stream.writeReset(code);
  }

  @Override
  public Future<HttpServerResponse> push(HttpMethod method, HostAndPort authority, String path, MultiMap headers) {
    if (push) {
      throw new IllegalStateException("A push response cannot promise another push");
    }
    if (authority == null) {
      authority = requestAuthority;
    }
    synchronized (conn) {
      checkValid();
    }
    HostAndPort h = authority;
    Future<HttpServerStream> fut = stream.sendPush(authority, method, headers, path, stream.priority());
    return fut.map(pushStream -> {
      HttpServerResponseImpl response = new HttpServerResponseImpl(pushStream, context, true);
      response.requestMethod = method;
      response.requestAuthority = h;
      PushStreamHandler push = new PushStreamHandler(pushStream, response, context);
      push.init();
      return push.response;
    });
  }

  private static class PushStreamHandler {

    protected final ContextInternal context;
    protected final HttpServerStream stream;
    protected final HttpServerResponseImpl response;

    public PushStreamHandler(HttpServerStream stream, HttpServerResponseImpl response, ContextInternal context) {
      this.context = context;
      this.stream = stream;
      this.response = response;
    }

    void init() {
      stream.headHandler(this::handleHeaders);
      stream.resetHandler(this::handleReset);
      stream.exceptionHandler(this::handleException);
      stream.closeHandler(response::handleClose);
      stream.dataHandler(this::handleData);
      stream.trailersHandler(this::handleTrailers);
      stream.customFrameHandler(this::handleCustomFrame);
      stream.priorityChangeHandler(this::handlePriorityChange);
      stream.drainHandler(response::handleWriteQueueDrained);
    }

    public void handleHeaders(HttpRequestHead headers) {
      // Do nothing ???
    }

    public void handleReset(long errorCode) {
      response.handleReset(errorCode);
    }

    public  void handleException(Throwable cause) {
      response.handleException(cause);
    }

    public void handleData(Buffer data) {
      throw new UnsupportedOperationException();
    }

    public void handleTrailers(MultiMap trailers) {
      throw new UnsupportedOperationException();
    }

    public void handleCustomFrame(HttpFrame frame) {
      throw new UnsupportedOperationException();
    }

    public void handlePriorityChange(StreamPriority streamPriority) {
      throw new UnsupportedOperationException();
    }

  }

  @Override
  public HttpServerResponse setStreamPriority(StreamPriority priority) {
    stream.updatePriority(priority);
    return this;
  }

  CookieJar cookies() {
    synchronized (conn) {
      // avoid double parsing
      if (cookies == null) {
        CharSequence cookieHeader = requestCookie;
        if (cookieHeader == null) {
          cookies = new CookieJar();
        } else {
          cookies = new CookieJar(cookieHeader);
        }
      }
    }
    return cookies;
  }

  @Override
  public HttpServerResponse addCookie(Cookie cookie) {
    synchronized (conn) {
      checkHeadWritten();
      cookies().add((ServerCookie) cookie);
    }
    return this;
  }

  @Override
  public @Nullable Cookie removeCookie(String name, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return cookies().removeOrInvalidate(name, invalidate);
    }
  }

  @Override
  public @Nullable Cookie removeCookie(String name, String domain, String path, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return cookies().removeOrInvalidate(name, domain, path, invalidate);
    }
  }

  @Override
  public @Nullable Set<Cookie> removeCookies(String name, boolean invalidate) {
    synchronized (conn) {
      checkHeadWritten();
      return (Set) cookies().removeOrInvalidateAll(name, invalidate);
    }
  }
}
