/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.*;
import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.MultiMap;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.impl.VertxInternal;

import java.io.File;

import static io.netty.handler.codec.http.HttpHeaders.Names;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServerResponse implements HttpServerResponse {

  private final VertxInternal vertx;
  private final ServerConnection conn;
  private final HttpResponse response;
  private final HttpVersion version;
  private final boolean keepAlive;

  private int statusCode = 200;
  private String statusMessage = "OK";

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

  DefaultHttpServerResponse(final VertxInternal vertx, ServerConnection conn, HttpRequest request) {
  	this.vertx = vertx;
  	this.conn = conn;
    this.version = request.getProtocolVersion();
    this.response = new DefaultHttpResponse(version, HttpResponseStatus.OK);
    this.keepAlive = version == HttpVersion.HTTP_1_1 ||
        (version == HttpVersion.HTTP_1_0 && "Keep-Alive".equalsIgnoreCase(request.headers().get("Connection")));
  }

  @Override
  public MultiMap headers() {
    if (headers == null) {
      headers = new HttpHeadersAdapter(response.headers());
    }
    return headers;
  }

  @Override
  public MultiMap trailers() {
    if (trailers == null) {
      if (trailing == null) {
        trailing = new DefaultLastHttpContent();
      }
      trailers = new HttpHeadersAdapter(trailing.trailingHeaders());
    }
    return trailers;
  }

  @Override
  public int getStatusCode() {
    return statusCode;
  }

  @Override
  public HttpServerResponse setStatusCode(int statusCode) {
    this.statusCode = statusCode;
    return this;
  }

  @Override
  public String getStatusMessage() {
    return statusMessage;
  }

  @Override
  public HttpServerResponse setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
    return this;
  }

  @Override
  public DefaultHttpServerResponse setChunked(boolean chunked) {
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
  public DefaultHttpServerResponse putHeader(String key, String value) {
    checkWritten();
    headers().set(key, value);
    return this;
  }

  @Override
  public DefaultHttpServerResponse putHeader(String key, Iterable<String> values) {
    checkWritten();
    headers().set(key, values);
    return this;
  }

  @Override
  public DefaultHttpServerResponse putTrailer(String key, String value) {
    checkWritten();
    trailers().set(key, value);
    return this;
  }

  @Override
  public DefaultHttpServerResponse putTrailer(String key, Iterable<String> values) {
    checkWritten();
    trailers().set(key, values);
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
    return conn.doWriteQueueFull();
  }

  @Override
  public HttpServerResponse drainHandler(Handler<Void> handler) {
    checkWritten();
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
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
  public DefaultHttpServerResponse write(Buffer chunk) {
    ByteBuf buf = chunk.getByteBuf();
    return write(buf, null);
  }

  @Override
  public DefaultHttpServerResponse write(String chunk, String enc) {
    return write(new Buffer(chunk, enc).getByteBuf(),  null);
  }

  @Override
  public DefaultHttpServerResponse write(String chunk) {
    return write(new Buffer(chunk).getByteBuf(), null);
  }

  @Override
  public void end(String chunk) {
    end(new Buffer(chunk));
  }

  @Override
  public void end(String chunk, String enc) {
    end(new Buffer(chunk, enc));
  }

  @Override
  public void end(Buffer chunk) {
    if (!chunked && !contentLengthSet()) {
      headers().set(Names.CONTENT_LENGTH, String.valueOf(chunk.length()));
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
        resp = new AssembledFullHttpResponse(response, data, trailing.trailingHeaders());
      }  else {
        resp = new AssembledFullHttpResponse(response, data);
      }
      channelFuture = conn.write(resp);
      headWritten = true;
    } else {
      if (!data.isReadable()) {
        if (trailing == null) {
          channelFuture = conn.write(LastHttpContent.EMPTY_LAST_CONTENT);
        } else {
          channelFuture = conn.write(trailing);
        }
      } else {
        LastHttpContent content;
        if (trailing != null) {
          content = new AssembledLastHttpContent(data, trailing.trailingHeaders());
        } else {
          content = new DefaultLastHttpContent(data);
        }
        channelFuture = conn.write(content);
      }
    }

    if (!keepAlive) {
      closeConnAfterWrite();
    }
    written = true;
    conn.responseComplete();
  }
  @Override
  public DefaultHttpServerResponse sendFile(String filename) {
    return sendFile(filename, null);
  }

  @Override
  public DefaultHttpServerResponse sendFile(String filename, String notFoundResource) {
    if (headWritten) {
      throw new IllegalStateException("Head already written");
    }
    checkWritten();
    File file = new File(PathAdjuster.adjust(vertx, filename));
    if (!file.exists()) {
      if (notFoundResource != null) {
        statusCode = HttpResponseStatus.NOT_FOUND.code();
        sendFile(notFoundResource, null);
      } else {
        sendNotFound();
      }
    } else {
      if (!contentLengthSet()) {
        putHeader(Names.CONTENT_LENGTH, String.valueOf(file.length()));
      }
      if (!contentTypeSet()) {
        int li = filename.lastIndexOf('.');
        if (li != -1 && li != filename.length() - 1) {
          String ext = filename.substring(li + 1, filename.length());
          String contentType = MimeMapping.getMimeTypeForExtension(ext);
          if (contentType != null) {
            putHeader(Names.CONTENT_TYPE, contentType);
          }
        }
      }
      prepareHeaders();
      conn.queueForWrite(response);
      conn.sendFile(file);

      // write an empty last content to let the http encoder know the response is complete
      channelFuture = conn.write(LastHttpContent.EMPTY_LAST_CONTENT);
      headWritten = written = true;

      if (!keepAlive) {
        closeConnAfterWrite();
      }
      conn.responseComplete();
    }
    return this;
  }

  private boolean contentLengthSet() {
    if (headers == null) {
      return false;
    }
    return headers.contains(Names.CONTENT_LENGTH);
  }

  private boolean contentTypeSet() {
    if (headers == null) {
      return false;
    }
    return headers.contains(Names.CONTENT_TYPE);
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

  private void sendNotFound() {
    statusCode = HttpResponseStatus.NOT_FOUND.code();
    putHeader("content-type", "text/html");
    end("<html><body>Resource not found</body><html>");
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
    HttpResponseStatus status = statusMessage == null ? HttpResponseStatus.valueOf(statusCode) :
            new HttpResponseStatus(statusCode, statusMessage);
    response.setStatus(status);
    if (version == HttpVersion.HTTP_1_0 && keepAlive) {
      response.headers().set("Connection", "Keep-Alive");
    }
    if (chunked) {
      response.headers().set(Names.TRANSFER_ENCODING, io.netty.handler.codec.http.HttpHeaders.Values.CHUNKED);
    } else if (version != HttpVersion.HTTP_1_0 && !contentLengthSet()) {
      response.headers().set(Names.CONTENT_LENGTH, "0");
    }
  }


  private DefaultHttpServerResponse write(ByteBuf chunk, final Handler<AsyncResult<Void>> doneHandler) {
    checkWritten();
    if (version != HttpVersion.HTTP_1_0 && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
                                              + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }

    if (!headWritten) {
      prepareHeaders();
      channelFuture = conn.write(new AssembledHttpResponse(response, chunk));
      headWritten = true;
    }  else {
      channelFuture = conn.write(new DefaultHttpContent(chunk));
    }

    conn.addFuture(doneHandler, channelFuture);
    return this;
  }
}
