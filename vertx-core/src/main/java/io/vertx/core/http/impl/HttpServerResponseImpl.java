/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.DefaultHttpResponse;
import io.netty.handler.codec.http.DefaultLastHttpContent;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.VertxInternal;

import java.io.File;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerResponseImpl implements HttpServerResponse {

  private static final Buffer NOT_FOUND = Buffer.buffer("<html><body>Resource not found</body><html>");
  private static final Buffer FORBIDDEN = Buffer.buffer("<html><body>Forbidden</body><html>");

  private final VertxInternal vertx;
  private final ServerConnection conn;
  private final HttpResponse response;
  private final HttpVersion version;
  private final boolean keepAlive;
  private boolean headWritten;
  private boolean written;
  private Handler<Void> drainHandler;
  private Handler<Throwable> exceptionHandler;
  private Handler<Void> closeHandler;
  private boolean chunked;
  private boolean closed;
  private ChannelFuture channelFuture;
  private MultiMap headers;
  private LastHttpContent trailing;
  private MultiMap trailers;
  private String statusMessage;

  HttpServerResponseImpl(final VertxInternal vertx, ServerConnection conn, HttpRequest request) {
  	this.vertx = vertx;
  	this.conn = conn;
    this.version = request.getProtocolVersion();
    this.response = new DefaultHttpResponse(version, HttpResponseStatus.OK, false);
    this.keepAlive = version == HttpVersion.HTTP_1_1 ||
        (version == HttpVersion.HTTP_1_0 && request.headers().contains(io.vertx.core.http.HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE, true));
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HeadersAdaptor(response.headers());
    }
    return headers;
  }

  @Override
  public MultiMap trailers() {
    if (trailers == null) {
      if (trailing == null) {
        trailing = new DefaultLastHttpContent(Unpooled.EMPTY_BUFFER, false);
      }
      trailers = new HeadersAdaptor(trailing.trailingHeaders());
    }
    return trailers;
  }

  @Override
  public int getStatusCode() {
    return response.getStatus().code();
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    HttpResponseStatus status = statusMessage != null ? new HttpResponseStatus(statusCode, statusMessage) : HttpResponseStatus.valueOf(statusCode);
    this.response.setStatus(status);
    return this;
  }

  @Override
  public String getStatusMessage() {
    return response.getStatus().reasonPhrase();
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
    this.response.setStatus(new HttpResponseStatus(response.getStatus().code(), statusMessage));
    return this;
  }

  @Override
  public HttpServerResponseImpl setChunked(boolean chunked) {
    checkWritten();
    // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
    if (version != HttpVersion.HTTP_1_0) {
      this.chunked = chunked;
    }
    return this;
  }

  @Override
  public boolean isChunked() {
    return chunked;
  }

  @Override
  public HttpServerResponseImpl putHeader(String key, String value) {
    checkWritten();
    headers().set(key, value);
    return this;
  }

  @Override
  public HttpServerResponseImpl putHeader(String key, Iterable<String> values) {
    checkWritten();
    headers().set(key, values);
    return this;
  }

  @Override
  public HttpServerResponseImpl putTrailer(String key, String value) {
    checkWritten();
    trailers().set(key, value);
    return this;
  }

  @Override
  public HttpServerResponseImpl putTrailer(String key, Iterable<String> values) {
    checkWritten();
    trailers().set(key, values);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, CharSequence value) {
    checkWritten();
    headers().set(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putHeader(CharSequence name, Iterable<CharSequence> values) {
    checkWritten();
    headers().set(name, values);
    return this;
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, CharSequence value) {
    checkWritten();
    trailers().set(name, value);
    return this;
  }

  @Override
  public HttpServerResponse putTrailer(CharSequence name, Iterable<CharSequence> value) {
    checkWritten();
    trailers().set(name, value);
    return this;
  }

  @Override
  public HttpServerResponse setWriteQueueMaxSize(int size) {
    checkWritten();
    conn.doSetWriteQueueMaxSize(size);
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    checkWritten();
    return conn.isNotWritable();
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    checkWritten();
    this.drainHandler = handler;
    conn.getContext().execute(conn::handleInterestedOpsChanged, false);
    return this;
  }

  @Override
  public HttpServerResponse exceptionHandler(Handler<Throwable> handler) {
    checkWritten();
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServerResponse closeHandler(Handler<Void> handler) {
    checkWritten();
    this.closeHandler = handler;
    return this;
  }

  @Override
  public HttpServerResponseImpl write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    return write(buf, null);
  }

  @Override
  public HttpServerResponseImpl write(String chunk, String enc) {
    return write(Buffer.buffer(chunk, enc).getByteBuf(),  null);
  }

  @Override
  public HttpServerResponseImpl write(String chunk) {
    return write(Buffer.buffer(chunk).getByteBuf(), null);
  }

  @Override
  public void end(String chunk) {
    end(Buffer.buffer(chunk));
  }

  @Override
  public void end(String chunk, String enc) {
    end(Buffer.buffer(chunk, enc));
  }

  @Override
  public void end(Buffer chunk) {
    if (!chunked && !contentLengthSet()) {
      headers().set(HttpHeaders.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    ByteBuf buf = chunk.getByteBuf();
    end0(buf);
  }

  @Override
  public void close() {
    if (!closed) {
      if (headWritten) {
        closeConnAfterWrite();
      } else {
        conn.close();
      }
      closed = true;
    }
  }

  @Override
  public void end() {
    end0(Unpooled.EMPTY_BUFFER);
  }

  private void end0(ByteBuf data) {
    checkWritten();

    if (!headWritten) {
      // if the head was not written yet we can write out everything in on go
      // which is more cheap.
      prepareHeaders();
      FullHttpResponse resp;
      if (trailing != null) {
        resp = new AssembledFullHttpResponse(response, data, trailing.trailingHeaders(), trailing.getDecoderResult());
      }  else {
        resp = new AssembledFullHttpResponse(response, data);
      }
      channelFuture = conn.writeToChannel(resp);
      headWritten = true;
    } else {
      if (!data.isReadable()) {
        if (trailing == null) {
          channelFuture = conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
          channelFuture = conn.writeToChannel(trailing);
        }
      } else {
        LastHttpContent content;
        if (trailing != null) {
          content = new AssembledLastHttpContent(data, trailing.trailingHeaders(), trailing.getDecoderResult());
        } else {
          content = new DefaultLastHttpContent(data, false);
        }
        channelFuture = conn.writeToChannel(content);
      }
    }

    if (!keepAlive) {
      closeConnAfterWrite();
    }
    written = true;
    conn.responseComplete();
  }

  @Override
  public HttpServerResponseImpl sendFile(String filename) {
    return sendFile(filename, (String)null);
  }

  @Override
  public HttpServerResponseImpl sendFile(String filename, String notFoundResource) {
    doSendFile(filename, notFoundResource, null);
    return this;
  }

  @Override
  public HttpServerResponse sendFile(String filename, String notFoundFile, Handler<AsyncResult<Void>> resultHandler) {
    doSendFile(filename, notFoundFile, resultHandler);
    return this;
  }

  @Override
  public HttpServerResponse sendFile(String filename, Handler<AsyncResult<Void>> resultHandler) {
    doSendFile(filename, null, resultHandler);
    return this;
  }

  @Override
  public boolean ended() {
    return written;
  }

  private void doSendFile(String filename, String notFoundResource, final Handler<AsyncResult<Void>> resultHandler) {
    if (headWritten) {
      throw new IllegalStateException("Head already written");
    }
    checkWritten();
    File file = vertx.resolveFile(filename);
    if (!file.exists()) {
      if (notFoundResource != null) {
        setStatusCode(HttpResponseStatus.NOT_FOUND.code());
        sendFile(notFoundResource, null, resultHandler);
      } else {
        sendNotFound();
      }
    } else if (file.isDirectory()) {
      // send over a 403 Forbidden
      sendForbidden();
    } else {
      if (!contentLengthSet()) {
        putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(file.length()));
      }
      if (!contentTypeSet()) {
        int li = filename.lastIndexOf('.');
        if (li != -1 && li != filename.length() - 1) {
          String ext = filename.substring(li + 1, filename.length());
          String contentType = MimeMapping.getMimeTypeForExtension(ext);
          if (contentType != null) {
            putHeader(HttpHeaders.CONTENT_TYPE, contentType);
          }
        }
      }
      prepareHeaders();
      conn.queueForWrite(response);
      conn.sendFile(file);

      // write an empty last content to let the http encoder know the response is complete
      channelFuture = conn.writeToChannel(LastHttpContent.EMPTY_LAST_CONTENT);
      headWritten = written = true;

      ContextImpl ctx = vertx.getOrCreateContext();
      if (resultHandler != null) {
        channelFuture.addListener(future -> {
          AsyncResult<Void> res;
          if (future.isSuccess()) {
            res = Future.completedFuture();
          } else {
            res = Future.completedFuture(future.cause());
          }
          ctx.execute(() -> resultHandler.handle(res), true);
        });
      }

      if (!keepAlive) {
        closeConnAfterWrite();
      }
      conn.responseComplete();
    }
  }


  private boolean contentLengthSet() {
    if (headers == null) {
      return false;
    }
    return response.headers().contains(HttpHeaders.CONTENT_LENGTH);
  }

  private boolean contentTypeSet() {
    if (headers == null) {
      return false;
    }
    return response.headers().contains(HttpHeaders.CONTENT_TYPE);
  }

  private void closeConnAfterWrite() {
    if (channelFuture != null) {
      channelFuture.addListener(new ChannelFutureListener() {
        public void operationComplete(ChannelFuture future) throws Exception {
          conn.close();
        }
      });
    }
  }

  private void sendForbidden() {
    setStatusCode(HttpResponseStatus.FORBIDDEN.code());
    putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML);
    end(FORBIDDEN);
  }

  private void sendNotFound() {
    setStatusCode(HttpResponseStatus.NOT_FOUND.code());
    putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML);
    end(NOT_FOUND);
  }

  void handleDrained() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  void handleException(Throwable t) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    }
  }

  void handleClosed() {
    if (closeHandler != null) {
      closeHandler.handle(null);
    }
  }

  private void checkWritten() {
    if (written) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  private void prepareHeaders() {
    if (version == HttpVersion.HTTP_1_0 && keepAlive) {
      response.headers().set(HttpHeaders.CONNECTION, HttpHeaders.KEEP_ALIVE);
    }
    if (chunked) {
      response.headers().set(HttpHeaders.TRANSFER_ENCODING, HttpHeaders.CHUNKED);
    } else if (version != HttpVersion.HTTP_1_0 && !contentLengthSet()) {
      response.headers().set(HttpHeaders.CONTENT_LENGTH, "0");
    }
  }


  private HttpServerResponseImpl write(ByteBuf chunk, final Handler<AsyncResult<Void>> completionHandler) {
    checkWritten();
    if (!headWritten && version != HttpVersion.HTTP_1_0 && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
                                              + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

    if (!headWritten) {
      prepareHeaders();
      channelFuture = conn.writeToChannel(new AssembledHttpResponse(response, chunk));
      headWritten = true;
    }  else {
      channelFuture = conn.writeToChannel(new DefaultHttpContent(chunk));
    }

    conn.addFuture(completionHandler, channelFuture);
    return this;
  }
}
