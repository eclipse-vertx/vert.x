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

/**
 * <p>Encapsulates a server-side HTTP response.</p>
 *
 * <p>An instance of this class is created and associated to every instance of {@link HttpServerRequest} that is created.<p>
 *
 * <p>It allows the developer to control the HTTP response that is sent back to the client for the corresponding HTTP
 * request. It contains methods that allow HTTP headers and trailers to be set, and for a body to be written out
 * to the response.</p>
 *
 * <p>It also allows a file to be streamed by the kernel directly from disk to the outgoing HTTP connection,
 * bypassing user space altogether (where supported by the underlying operating system). This is a very efficient way of
 * serving files from the server since buffers do not have to be read one by one from the file and written to the outgoing
 * socket.</p>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
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

  /**
   * The HTTP status code of the response. The default is {@code 200} representing {@code OK}.
   */
  public int statusCode = HttpResponseStatus.OK.getCode();

  /**
   * The HTTP status message of the response. If this is not specified a default value will be used depending on what
   * {@link #statusCode} has been set to.
   *
   */
  public String statusMessage = null;

  /**
   * If {@code chunked} is {@code true}, this response will use HTTP chunked encoding, and each call to write to the body
   * will correspond to a new HTTP chunk sent on the wire. If chunked encoding is used the HTTP header
   * {@code Transfer-Encoding} with a value of {@code Chunked} will be automatically inserted in the response.<p>
   * If {@code chunked} is {@code false}, this response will not use HTTP chunked encoding, and therefore if any data is written the
   * body of the response, the total size of that data must be set in the {@code Content-Length} header <b>before</b> any
   * data is written to the response body. If no data is written, then a {@code Content-Length} header with a value of {@code 0}
   * will be automatically inserted when the response is sent.<p>
   * An HTTP chunked response is typically used when you do not know the total size of the request body up front.
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse setChunked(boolean chunked) {
    checkWritten();
    if (writtenBytes > 0) {
      throw new IllegalStateException("Cannot set chunked after data has been written on response");
    }
    this.chunked = chunked;
    return this;
  }

  /**
   * Inserts a header into the response. The {@link Object#toString()} method will be called on {@code value} to determine
   * the String value to actually use for the header value.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse putHeader(String key, Object value) {
    checkWritten();
    response.setHeader(key, value);
    checkContentLengthChunked(key, value);
    return this;
  }

  /**
   * Inserts all the specified headers into the response. The {@link Object#toString()} method will be called on the header values {@code value} to determine
   * the String value to actually use for the header value.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse putAllHeaders(Map<String, ? extends Object> m) {
    checkWritten();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      response.setHeader(entry.getKey(), entry.getValue().toString());
      checkContentLengthChunked(entry.getKey(), entry.getValue());
    }
    return this;
  }

  /**
   * Inserts a trailer into the response. The {@link Object#toString()} method will be called on {@code value} to determine
   * the String value to actually use for the trailer value.<p>
   * Trailers are only sent if you are using a HTTP chunked response.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse putTrailer(String key, Object value) {
    checkWritten();
    checkTrailer();
    trailer.setHeader(key, value);
    return this;
  }

  /**
   * Inserts all the specified trailers into the response. The {@link Object#toString()} method will be called on {@code value} to determine
   * the String value to actually use for the trailer value.<p>
   * Trailers are only sent if you are using a HTTP chunked response.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse putAllTrailers(Map<String, ? extends Object> m) {
    checkWritten();
    checkTrailer();
    for (Map.Entry<String, ? extends Object> entry : m.entrySet()) {
      trailer.setHeader(entry.getKey(), entry.getValue().toString());
    }
    return this;
  }

  /**
   * Data is queued until it is actually sent. To set the point at which the queue is considered "full" call this method
   * specifying the {@code maxSize} in bytes.<p>
   * This method is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   */
  public void setWriteQueueMaxSize(int size) {
    checkWritten();
    conn.setWriteQueueMaxSize(size);
  }

  /**
   * If the amount of data that is currently queued is greater than the write queue max size see {@link #setWriteQueueMaxSize(int)}
   * then the response queue is considered full.<p>
   * Data can still be written to the response even if the write queue is deemed full, however it should be used as indicator
   * to stop writing and push back on the source of the data, otherwise you risk running out of available RAM.<p>
   * This method is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   * @return {@code true} if the write queue is full, {@code false} otherwise
   */
  public boolean writeQueueFull() {
    checkWritten();
    return conn.writeQueueFull();
  }

  /**
   * This method sets a drain handler {@code handler} on the response. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.nodex.java.core.streams.Pump} class to pump data
   * between different streams.
   * @param handler
   */
  public void drainHandler(EventHandler<Void> handler) {
    checkWritten();
    this.drainHandler = handler;
    conn.handleInterestedOpsChanged(); //If the channel is already drained, we want to call it immediately
  }

  /**
   * Set {@code handler} as an exception handler on the response. Any exceptions that occur
   * will be notified by calling the handler. If the response has no handler than any exceptions occurring will be
   * output to {@link System#err}
   */
  public void exceptionHandler(EventHandler<Exception> handler) {
    checkWritten();
    this.exceptionHandler = handler;
  }

  /**
   * Write a {@link Buffer} to the response body.
   */
  public void writeBuffer(Buffer chunk) {
    write(chunk.getChannelBuffer(), null);
  }

  /**
   * Write a {@link Buffer} to the response body.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse write(Buffer chunk) {
    return write(chunk.getChannelBuffer(), null);
  }

  /**
   * Write a {@link String} to the response body, encoded using the encoding {@code enc}.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse write(String chunk, String enc) {
    return write(Buffer.create(chunk, enc).getChannelBuffer(), null);
  }

  /**
   * Write a {@link String} to the response body, encoded in UTF-8.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse write(String chunk) {
    return write(Buffer.create(chunk).getChannelBuffer(), null);
  }

  /**
   * Write a {@link Buffer} to the response body. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse write(Buffer chunk, EventHandler<Void> doneHandler) {
    return write(chunk.getChannelBuffer(), doneHandler);
  }

  /**
   * Write a {@link String} to the response body, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse write(String chunk, String enc, EventHandler<Void> doneHandler) {
    return write(Buffer.create(chunk, enc).getChannelBuffer(), doneHandler);
  }

  /**
   * Write a {@link String} to the response body, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   * @return A reference to this, so multiple method calls can be chained.
   */
  public HttpServerResponse write(String chunk, EventHandler<Void> doneHandler) {
    return write(Buffer.create(chunk).getChannelBuffer(), doneHandler);
  }

  /**
   * Ends the response. If no data has been written to the request body, the actual request won't get written until this method gets called.<p>
   * Once the response has ended, it cannot be used any more, and if keep alive is true the underlying connection will
   * be closed.
   */
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

  /**
   * Tell the kernel to stream a file as specified by {@code filename} directly from disk to the outgoing connection, bypassing userspace altogether
   * (where supported by the underlying operating system. This is a very efficient way to serve files.<p>
   */
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
      HttpResponseStatus status = statusMessage == null ? HttpResponseStatus.valueOf(statusCode) :
                                  new HttpResponseStatus(statusCode, statusMessage);
      response.setStatus(status);
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
