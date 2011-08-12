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

package org.nodex.core.http;

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
import org.nodex.core.ExceptionHandler;
import org.nodex.core.buffer.Buffer;
import org.nodex.core.streams.WriteStream;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.jboss.netty.handler.codec.http.HttpHeaders.Names;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

public class HttpServerResponse implements WriteStream {
  private static final String HTTP_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";
  private static final String HTTP_DATE_GMT_TIMEZONE = "GMT";

  private final boolean keepAlive;
  private final HttpServerConnection conn;
  private final HttpResponse response;
  private HttpChunkTrailer trailer;

  private boolean headWritten;
  private ChannelFuture writeFuture;
  private boolean written;
  private Runnable drainHandler;
  private ExceptionHandler exceptionHandler;

  HttpServerResponse(boolean keepAlive, HttpServerConnection conn) {
    this.keepAlive = keepAlive;
    this.conn = conn;
    this.response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.OK);
  }

  public int statusCode = HttpResponseStatus.OK.getCode();

  public HttpServerResponse putHeader(String key, Object value) {
    response.setHeader(key, value);
    return this;
  }

  public HttpServerResponse putHeaders(String key, Iterable<String> values) {
    response.setHeader(key, values);
    return this;
  }

  public HttpServerResponse addHeader(String key, Object value) {
    response.addHeader(key, value);
    return this;
  }

  public HttpServerResponse putAllHeaders(Map<String, ? extends Object> m) {
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      response.setHeader(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public HttpServerResponse putTrailer(String key, Object value) {
    checkTrailer();
    trailer.setHeader(key, value);
    return this;
  }

  public HttpServerResponse putTrailers(String key, Iterable<String> values) {
    checkTrailer();
    trailer.setHeader(key, values);
    return this;
  }

  public HttpServerResponse addTrailer(String key, Object value) {
    checkTrailer();
    trailer.addHeader(key, value);
    return this;
  }

  public HttpServerResponse putAllTrailers(Map<String, ? extends Object> m) {
    checkTrailer();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      trailer.setHeader(entry.getKey(), entry.getValue());
    }
    return this;
  }

  public void setWriteQueueMaxSize(int size) {
    conn.setWriteQueueMaxSize(size);
  }

  public boolean writeQueueFull() {
    return conn.writeQueueFull();
  }

  public void drainHandler(Runnable handler) {
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  public void exceptionHandler(ExceptionHandler handler) {
    this.exceptionHandler = handler;
  }

  public void writeBuffer(Buffer chunk) {
    write(chunk._getChannelBuffer(), null);
  }

  public HttpServerResponse write(Buffer chunk) {
    return write(chunk._getChannelBuffer(), null);
  }

  public HttpServerResponse write(String chunk, String enc) {
    return write(Buffer.create(chunk, enc)._getChannelBuffer(), null);
  }

  public HttpServerResponse write(String chunk) {
    return write(Buffer.create(chunk)._getChannelBuffer(), null);
  }

  public HttpServerResponse write(Buffer chunk, Runnable done) {
    return write(chunk._getChannelBuffer(), done);
  }

  public HttpServerResponse write(String chunk, String enc, Runnable done) {
    return write(Buffer.create(chunk, enc)._getChannelBuffer(), done);
  }

  public HttpServerResponse write(String chunk, Runnable done) {
    return write(Buffer.create(chunk)._getChannelBuffer(), done);
  }

  public void end() {
    if (!headWritten) {
      //No body
      response.setStatus(HttpResponseStatus.valueOf(statusCode));
      response.setHeader(CONTENT_LENGTH, 0);
      writeFuture = conn.write(response);
    } else {
      //Body written - We use HTTP chunking so we need to write a zero length chunk to signify the endHandler
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
      throw new IllegalStateException("Response complete");
    }

    File file = new File(filename);

    if (!file.exists()) {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, HttpResponseStatus.NOT_FOUND);
      writeFuture = conn.write(response);
    } else {
      HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
      response.setHeader(Names.CONTENT_LENGTH, String.valueOf(file.length()));
      try {
        String contenttype = Files.probeContentType(Paths.get(filename));
        response.setHeader(Names.CONTENT_TYPE, contenttype);
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
      drainHandler.run();
    }
  }

  void handleException(Exception e) {
    if (exceptionHandler != null) {
      exceptionHandler.onException(e);
    }
  }

  private void checkTrailer() {
    if (trailer == null) trailer = new DefaultHttpChunkTrailer();
  }

  /*
  We use HTTP chunked encoding and each write has it's own chunk
  TODO non chunked encoding
  Non chunked encoding does not work well with async writes since normally do not know Content-Length in advance
  and need to know this for non chunked encoding
   */
  private HttpServerResponse write(ChannelBuffer chunk, final Runnable done) {
    if (written) {
      throw new IllegalStateException("Response complete");
    }

    if (!headWritten) {
      response.setStatus(HttpResponseStatus.valueOf(statusCode));
      response.setChunked(true);
      response.setHeader(Names.TRANSFER_ENCODING, HttpHeaders.Values.CHUNKED);
      conn.write(response);
      headWritten = true;
    }

    HttpChunk nettyChunk = new DefaultHttpChunk(chunk);
    writeFuture = conn.write(nettyChunk);
    if (done != null) {
      conn.addFuture(done, writeFuture);
    }

    return this;
  }
}
