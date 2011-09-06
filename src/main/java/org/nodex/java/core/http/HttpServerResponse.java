/*
 * Copyright 2002-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package org.nodex.java.core.http;

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
import org.nodex.java.core.EventHandler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.java.core.streams.WriteStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpServerResponse implements WriteStream {

  private final boolean keepAlive;
  private final ServerConnection conn;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;

  private boolean headWritten;
  private ChannelFuture writeFuture;
  private boolean written;
  private EventHandler<Void> drainHandler;
  private EventHandler<Exception> exceptionHandler;
  private long contentLength;
  private long writtenBytes;
  private boolean chunked;

  HttpServerResponse(boolean keepAlive, ServerConnection conn) {
    this.keepAlive = keepAlive;
    this.conn = conn;
    this.response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
  }

  public int statusCode = HttpResponseStatus.OK.getCode();

  public HttpServerResponse setChunked(boolean chunked) {
    checkWritten();
    if (writtenBytes > 0) {
      throw new IllegalStateException("Cannot set chunked after data has been written on response");
    }
    this.chunked = chunked;
    return this;
  }

  public HttpServerResponse putHeader(String key, Object value) {
    checkWritten();
    response.setHeader(key, value);
    checkContentLengthChunked(key, value);
    return this;
  }

  public HttpServerResponse putAllHeaders(Map<String, ? extends Object> m) {
    checkWritten();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      response.setHeader(entry.getKey(), entry.getValue().toString());
      checkContentLengthChunked(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public HttpServerResponse putTrailer(String key, Object value) {
    checkWritten();
    checkTrailer();
    trailer.setHeader(key, value);
    return this;
  }

  public HttpServerResponse putAllTrailers(Map<String, ? extends Object> m) {
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

  public void drainHandler(EventHandler<Void> handler) {
    checkWritten();
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  public void exceptionHandler(EventHandler<Exception> handler) {
    checkWritten();
    this.exceptionHandler = handler;
  }

  public void writeBuffer(Buffer chunk) {
    write(chunk.getChannelBuffer(), null);
  }

  public HttpServerResponse write(Buffer chunk) {
    return write(chunk.getChannelBuffer(), null);
  }

  public HttpServerResponse write(String chunk, String enc) {
    return write(Buffer.create(chunk, enc).getChannelBuffer(), null);
  }

  public HttpServerResponse write(String chunk) {
    return write(Buffer.create(chunk).getChannelBuffer(), null);
  }

  public HttpServerResponse write(Buffer chunk, EventHandler<Void> doneHandler) {
    return write(chunk.getChannelBuffer(), doneHandler);
  }

  public HttpServerResponse write(String chunk, String enc, EventHandler<Void> doneHandler) {
    return write(Buffer.create(chunk, enc).getChannelBuffer(), doneHandler);
  }

  public HttpServerResponse write(String chunk, EventHandler<Void> doneHandler) {
    return write(Buffer.create(chunk).getChannelBuffer(), doneHandler);
  }

  public void end() {
    checkWritten();
    writeHead();
    if (chunked) {
      HttpChunk nettyChunk;
      if (trailer == null) {
        nettyChunk = new DefaultHttpChunk(ChannelBuffers.EMPTY_BUFFER);
      } else {
        nettyChunk = trailer;
      }
      writeFuture = conn.write(nettyChunk);
    }
    // Close the non-keep-alive connection after the write operation is done.
    if (!keepAlive) {
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
    written = true;
    conn.responseComplete();
  }

  public HttpServerResponse sendFile(String filename) {
    if (headWritten) {
      throw new IllegalStateException("Head already written");
    }
    checkWritten();

    File file = new File(filename);

    if (!file.exists()) {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_FOUND);
      writeFuture = conn.write(response);
    } else {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.setHeader(Names.CONTENT_LENGTH, String.valueOf(file.length()));
      try {
        String contenttype = Files.probeContentType(Paths.get(filename));
        if (contenttype != null) {
          response.setHeader(Names.CONTENT_TYPE, contenttype);
        }
      } catch (IOException e) {
        e.printStackTrace();
      }

      conn.write(response);

      writeFuture = conn.sendFile(file);
    }

    // Close the non-keep-alive connection after the write operation is done.
    if (!keepAlive) {
      writeFuture.addListener(ChannelFutureListener.CLOSE);
    }
    headWritten = written = true;
    conn.responseComplete();

    return this;
  }

  void writable() {
    if (drainHandler != null) {
      drainHandler.onEvent(null);
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onEvent(e);
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
    if (key.equals(HttpHeaders.Names.CONTENT_LENGTH)) {
      contentLength = Integer.parseInt(value.toString());
      chunked = false;
    } else if (key.equals(HttpHeaders.Names.TRANSFER_ENCODING) && value.equals(HttpHeaders.Values.CHUNKED)) {
      chunked = true;
    }
  }

  private void writeHead() {
    if (!headWritten) {
      response.setStatus(HttpResponseStatus.valueOf(statusCode));
      if (chunked) {
        response.setHeader(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
      } else if (contentLength == 0) {
        response.setHeader(HttpHeaders.Names.CONTENT_LENGTH, "0");
      }
      writeFuture = conn.write(response);
      headWritten = true;
    }
  }

  private HttpServerResponse write(ChannelBuffer chunk, final EventHandler<Void> doneHandler) {
    checkWritten();
    writtenBytes += chunk.readableBytes();
    if (!chunked && writtenBytes > contentLength) {
      throw new IllegalStateException("You must set the Content-Length header to be the total size of the message "
          + "body BEFORE sending any data if you are not using HTTP chunked encoding. "
          + "Current written: " + written + " Current Content-Length: " + contentLength);
    }

    writeHead();
    Object msg = chunked ? new DefaultHttpChunk(chunk) : chunk;
    writeFuture = conn.write(msg);
    if (doneHandler != null) {
      conn.addFuture(doneHandler, writeFuture);
    }
    return this;
  }
}
