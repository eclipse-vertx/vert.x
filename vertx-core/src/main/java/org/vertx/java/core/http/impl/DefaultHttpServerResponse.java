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

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.handler.codec.http.*;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.file.impl.PathAdjuster;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.impl.LowerCaseKeyMap;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServerResponse extends HttpServerResponse {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(DefaultHttpServerResponse.class);

  private final ServerConnection conn;
  private final HttpResponse response;
  private final HttpVersion version;
  private final boolean keepAlive;
  private boolean headWritten;
  private boolean written;
  private Handler<Void> drainHandler;
  private Handler<Exception> exceptionHandler;
  private Handler<Void> closeHandler;
  private boolean chunked;
  private boolean closed;
  private ChannelFuture channelFuture;
  private Map<String, Object> headers;
  private Map<String, Object> trailers;
  private final VertxInternal vertx;

  DefaultHttpServerResponse(final VertxInternal vertx, ServerConnection conn, HttpVersion version, boolean keepAlive) {
  	this.vertx = vertx;
  	this.conn = conn;
    this.response = new DefaultHttpResponse(version, HttpResponseStatus.OK);
    this.version = version;
    this.keepAlive = keepAlive;
  }

  public Map<String, Object> headers() {
    if (headers == null) {
      headers = new LowerCaseKeyMap();
    }
    return headers;
  }

  public Map<String, Object> trailers() {
    if (trailers == null) {
      trailers = new LowerCaseKeyMap();
    }
    return trailers;
  }

  public DefaultHttpServerResponse setChunked(boolean chunked) {
    checkWritten();
    // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
    if (version != HttpVersion.HTTP_1_0) {
      this.chunked = chunked;
    }
    return this;
  }

  public DefaultHttpServerResponse putHeader(String key, Object value) {
    checkWritten();
    headers().put(key, value);
    return this;
  }

  public DefaultHttpServerResponse putTrailer(String key, Object value) {
    checkWritten();
    trailers().put(key, value);
    return this;
  }

  public void setWriteQueueMaxSize(int size) {
    checkWritten();
    conn.setWriteQueueMaxSize(size);
  }

  public boolean writeQueueFull() {
    checkWritten();
    return conn.writeQueueFull();
  }

  public void drainHandler(Handler<Void> handler) {
    checkWritten();
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  public void exceptionHandler(Handler<Exception> handler) {
    checkWritten();
    this.exceptionHandler = handler;
  }

  public void closeHandler(Handler<Void> handler) {
    checkWritten();
    this.closeHandler = handler;
  }

  public void writeBuffer(Buffer chunk) {
    write(chunk.getChannelBuffer(), null);
  }

  public DefaultHttpServerResponse write(Buffer chunk) {
    return write(chunk.getChannelBuffer(), null);
  }

  public DefaultHttpServerResponse write(String chunk, String enc) {
    return write(new Buffer(chunk, enc).getChannelBuffer(), null);
  }

  public DefaultHttpServerResponse write(String chunk) {
    return write(new Buffer(chunk).getChannelBuffer(), null);
  }

  public DefaultHttpServerResponse write(Buffer chunk, Handler<Void> doneHandler) {
    return write(chunk.getChannelBuffer(), doneHandler);
  }

  public DefaultHttpServerResponse write(String chunk, String enc, Handler<Void> doneHandler) {
    return write(new Buffer(chunk, enc).getChannelBuffer(), doneHandler);
  }

  public DefaultHttpServerResponse write(String chunk, Handler<Void> doneHandler) {
    return write(new Buffer(chunk).getChannelBuffer(), doneHandler);
  }

  public void end(String chunk) {
    end(new Buffer(chunk));
  }

  public void end(String chunk, String enc) {
    end(new Buffer(chunk, enc));
  }

  public void end(Buffer chunk) {
    if (!chunked && !contentLengthSet()) {
      headers().put(Names.CONTENT_LENGTH, String.valueOf(chunk.length()));
    }
    write(chunk);
    end();
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

  public void end() {

    checkWritten();
    writeHead();
    if (chunked) {
      if (trailers == null) {
        HttpChunk nettyChunk = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
        channelFuture = conn.write(nettyChunk);
      } else {
        DefaultHttpChunkTrailer trlrs = new DefaultHttpChunkTrailer();
        for (Map.Entry<String, Object> trailer: trailers.entrySet()) {
          Object value = trailer.getValue();
          if (value instanceof Iterable<?>) {
            trlrs.setHeader(trailer.getKey(), (Iterable<?>) value);
          } else {
            trlrs.setHeader(trailer.getKey(), value);
          }
        }
        channelFuture = conn.write(trlrs);
      }
    }

    if (!keepAlive) {
      closeConnAfterWrite();
    }

    written = true;
    conn.responseComplete();
  }

  private boolean contentLengthSet() {
    if (headers != null) {
      return headers.containsKey(Names.CONTENT_LENGTH);
    } else {
      return false;
    }
  }

  private boolean contentTypeSet() {
    if (headers != null) {
      return headers.containsKey(Names.CONTENT_TYPE);
    } else {
      return false;
    }
  }

  public DefaultHttpServerResponse sendFile(String filename) {
    if (headWritten) {
      throw new IllegalStateException("Head already written");
    }
    checkWritten();
    File file = new File(PathAdjuster.adjust(vertx, filename));
    if (!file.exists()) {
      sendNotFound();
    } else {
      writeHeaders();
      if (!contentLengthSet()) {
        response.setHeader(Names.CONTENT_LENGTH, String.valueOf(file.length()));
      }
      if (!contentTypeSet()) {
        int li = filename.lastIndexOf('.');
        if (li != -1 && li != filename.length() - 1) {
          String ext = filename.substring(li + 1, filename.length());
          String contentType = MimeMapping.getMimeTypeForExtension(ext);
          if (contentType != null) {
            response.setHeader(Names.CONTENT_TYPE, contentType);
          }
        }
      }

      conn.write(response);
      channelFuture = conn.sendFile(file);
      headWritten = written = true;
      
      if (!keepAlive) {
        closeConnAfterWrite();
      }
      
      conn.responseComplete();
    }

    return this;
  }

  private void sendNotFound() {
    statusCode = HttpResponseStatus.NOT_FOUND.getCode();
    end("<html><body>Resource not found</body><html>");
  }

  void handleDrained() {
    if (drainHandler != null) {
      drainHandler.handle(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(e);
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

  private void writeHead() {
    if (!headWritten) {
      HttpResponseStatus status = statusMessage == null ? HttpResponseStatus.valueOf(statusCode) :
          new HttpResponseStatus(statusCode, statusMessage);
      response.setStatus(status);
      if (version == HttpVersion.HTTP_1_0 && keepAlive) {
        response.setHeader("Connection", "Keep-Alive");
      }
      writeHeaders();
      if (chunked) {
        response.setHeader(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
      } else if (version != HttpVersion.HTTP_1_0 && !contentLengthSet()) {
        response.setHeader(Names.CONTENT_LENGTH, "0");
      }
      channelFuture = conn.write(response);
      headWritten = true;
    }
  }

  private void writeHeaders() {
    if (headers != null) {
      for (Map.Entry<String, Object> header: headers.entrySet()) {
        String key = header.getKey();
        Object value = header.getValue();
        if (value instanceof Iterable<?>) {
          response.setHeader(key, (Iterable<?>) value);
        } else {
          response.setHeader(key, value);
        }
      }
    }
  }

  private DefaultHttpServerResponse write(ChannelBuffer chunk, final Handler<Void> doneHandler) {
    checkWritten();
    writeHead();
    if (version != HttpVersion.HTTP_1_0 && !chunked && !contentLengthSet()) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding.");
    }
    Object msg = chunked ? new DefaultHttpChunk(chunk) : chunk;
    channelFuture = conn.write(msg);
    if (doneHandler != null) {
      conn.addFuture(doneHandler, channelFuture);
    }
    return this;
  }
}
