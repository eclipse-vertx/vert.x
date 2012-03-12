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
import org.jboss.netty.handler.codec.http.DefaultHttpChunk;
import org.jboss.netty.handler.codec.http.DefaultHttpChunkTrailer;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpChunk;
import org.jboss.netty.handler.codec.http.HttpChunkTrailer;
import org.jboss.netty.handler.codec.http.HttpHeaders;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.HttpVersion;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpServerResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DefaultHttpServerResponse extends HttpServerResponse {

  private static final Logger log = LoggerFactory.getLogger(DefaultHttpServerResponse.class);

  private final ServerConnection conn;
  private final HttpResponse response;
  private final HttpVersion version;
  private final boolean keepAlive;
  private HttpChunkTrailer trailer;
  private boolean headWritten;
  private boolean written;
  private Handler<Void> drainHandler;
  private Handler<Exception> exceptionHandler;
  private Handler<Void> closeHandler;
  private long contentLength;
  private long writtenBytes;
  private boolean chunked;

  DefaultHttpServerResponse(ServerConnection conn, HttpVersion version, boolean keepAlive) {
    this.conn = conn;
    this.response = new DefaultHttpResponse(version, HttpResponseStatus.OK);
    this.version = version;
    this.keepAlive = keepAlive;
  }

  public DefaultHttpServerResponse setChunked(boolean chunked) {
    checkWritten();
    // HTTP 1.0 does not support chunking so we ignore this if HTTP 1.0
    if (version != HttpVersion.HTTP_1_0) {
      if (writtenBytes > 0) {
        throw new IllegalStateException("Cannot set chunked after data has been written on response");
      }
      this.chunked = chunked;
    }
    return this;
  }

  public DefaultHttpServerResponse putHeader(String key, Object value) {
    checkWritten();
    response.setHeader(key, value);
    checkContentLengthChunked(key, value);
    return this;
  }

  public DefaultHttpServerResponse putAllHeaders(Map<String, ? extends Object> m) {
    checkWritten();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      response.setHeader(entry.getKey(), entry.getValue().toString());
      checkContentLengthChunked(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public DefaultHttpServerResponse putTrailer(String key, Object value) {
    checkChunked();
    checkWritten();
    checkTrailer();
    trailer.setHeader(key, value);
    return this;
  }

  public DefaultHttpServerResponse putAllTrailers(Map<String, ? extends Object> m) {
    checkChunked();
    checkWritten();
    checkTrailer();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      trailer.setHeader(entry.getKey(), entry.getValue().toString());
    }
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
    return write(Buffer.create(chunk, enc).getChannelBuffer(), null);
  }

  public DefaultHttpServerResponse write(String chunk) {
    return write(Buffer.create(chunk).getChannelBuffer(), null);
  }

  public DefaultHttpServerResponse write(Buffer chunk, Handler<Void> doneHandler) {
    return write(chunk.getChannelBuffer(), doneHandler);
  }

  public DefaultHttpServerResponse write(String chunk, String enc, Handler<Void> doneHandler) {
    return write(Buffer.create(chunk, enc).getChannelBuffer(), doneHandler);
  }

  public DefaultHttpServerResponse write(String chunk, Handler<Void> doneHandler) {
    return write(Buffer.create(chunk).getChannelBuffer(), doneHandler);
  }

  public void end(String chunk) {
    end(chunk, false);
  }

  public void end(String chunk, boolean closeConnection) {
    end(Buffer.create(chunk), closeConnection);
  }

  public void end(String chunk, String enc) {
    end(chunk, enc, false);
  }

  public void end(String chunk, String enc, boolean closeConnection) {
    end(Buffer.create(chunk, enc), closeConnection);
  }

  public void end(Buffer chunk) {
    end(chunk, false);
  }

  public void end(Buffer chunk, boolean closeConnection) {
    if (!chunked && contentLength == 0) {
      contentLength = chunk.length();
      response.setHeader(Names.CONTENT_LENGTH, String.valueOf(contentLength));
    }
    write(chunk);
    end(closeConnection);
  }

  public void end() {
    end(false);
  }

  private ChannelFuture channelFuture;

  private void closeConnection() {
    channelFuture.addListener(new ChannelFutureListener() {
      public void operationComplete(ChannelFuture future) throws Exception {
        conn.close();
      }
    });
  }

  public void end(boolean closeConnection) {

    if (written) {
      if (closeConnection) {
        closeConnection();
      }
      // Already written - ignore
      return;
    }

    checkWritten();
    writeHead();
    if (chunked) {
      HttpChunk nettyChunk;
      if (trailer == null) {
        nettyChunk = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
      } else {
        nettyChunk = trailer;
      }
      channelFuture = conn.write(nettyChunk);
    }

    if (closeConnection || !keepAlive) {
      closeConnection();
    }

    written = true;
    conn.responseComplete();
  }

  public DefaultHttpServerResponse sendFile(String filename) {
    if (headWritten) {
      throw new IllegalStateException("Head already written");
    }
    checkWritten();
    File file = new File(filename);
    if (!file.exists()) {
      sendNotFound();
    } else {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.setHeader(Names.CONTENT_LENGTH, String.valueOf(file.length()));
      try {
        String contentType = Files.probeContentType(Paths.get(filename));
        if (contentType != null) {
          response.setHeader(Names.CONTENT_TYPE, contentType);
        }
      } catch (IOException e) {
        log.error("Failed to get content type", e);
        sendNotFound();
      }

      conn.write(response);
      channelFuture = conn.sendFile(file);
      headWritten = written = true;
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

  private void checkChunked() {
    if (!chunked) {
      throw new IllegalStateException("Cannot set trailer for non-chunked response");
    }
  }

  private void checkTrailer() {
    if (trailer == null) trailer = new DefaultHttpChunkTrailer();
  }

  private void checkWritten() {
    if (written) {
      throw new IllegalStateException("Response has already been written");
    }
  }

  private void checkContentLengthChunked(String key, Object value) {
    if (key.equals(Names.CONTENT_LENGTH)) {
      contentLength = Integer.parseInt(value.toString());
      chunked = false;
    } else if (key.equals(Names.TRANSFER_ENCODING) && value.equals(HttpHeaders.Values.CHUNKED)) {
      chunked = true;
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
      if (chunked) {
        response.setHeader(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
      } else if (version != HttpVersion.HTTP_1_0 && contentLength == 0) {
        response.setHeader(Names.CONTENT_LENGTH, "0");
      }
      channelFuture = conn.write(response);
      headWritten = true;
    }
  }

  private DefaultHttpServerResponse write(ChannelBuffer chunk, final Handler<Void> doneHandler) {
    checkWritten();
    writtenBytes += chunk.readableBytes();
    if (version != HttpVersion.HTTP_1_0 && !chunked && writtenBytes > contentLength) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding. "
          + "Current written: " + written + " Current Content-Length: " + contentLength);
    }

    writeHead();
    Object msg = chunked ? new DefaultHttpChunk(chunk) : chunk;
    ChannelFuture writeFuture = conn.write(msg);
    if (doneHandler != null) {
      conn.addFuture(doneHandler, writeFuture);
    }
    return this;
  }
}
