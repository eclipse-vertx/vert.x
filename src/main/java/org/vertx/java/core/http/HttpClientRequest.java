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

package org.vertx.java.core.http;

import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.LoggerFactory;
import org.vertx.java.core.streams.WriteStream;

import java.util.Map;

/**
 * Represents a client-side HTTP request.<p>
 * Instances of this class are created by an {@link HttpClient} instance, via one of the methods corresponding to the
 * specific HTTP methods, or the generic {@link HttpClient#request} method
 * <p>
 * Once an instance of this class has been obtained, headers can be set on it, and data can be written to its body,
 * if required. Once you are ready to send the request, the {@link #end()} method must called.
 * <p>
 * Nothing is sent until the request has been internally assigned an HTTP connection. The {@link HttpClient} instance
 * will return an instance of this class immediately, even if there are no HTTP connections available in the pool. Any requests
 * sent before a connection is assigned will be queued internally and actually sent when an HTTP connection becomes
 * available from the pool.
 * <p>
 * The headers of the request are actually sent either when the {@link #end()} method is called, or, when the first
 * part of the body is written, whichever occurs first.
 * <p>
 * This class supports both chunked and non-chunked HTTP.
 * <p>
 * An example of using this class is as follows:
 * <p>
 * <pre>
 *
 * HttpClientRequest req = httpClient.post("/some-url", new Handler<HttpClientResponse>() {
 *   public void handle(HttpClientResponse response) {
 *     System.out.println("Got response: " + response.statusCode);
 *   }
 * });
 *
 * req.putHeader("some-header", "hello");
 * req.putHeader("Content-Length", 5);
 * req.write(Buffer.create(new byte[]{1, 2, 3, 4, 5}));
 * req.write(Buffer.create(new byte[]{6, 7, 8, 9, 10}));
 * req.end();
 *
 * </pre>
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class HttpClientRequest implements WriteStream {

  private static final Logger log = LoggerFactory.getLogger(HttpClient.class);

  protected HttpClientRequest() {
  }

  /**
   * If chunked is true then the request will be set into HTTP chunked mode
   * @param chunked
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest setChunked(boolean chunked);

  /**
   * Inserts a header into the request. The {@link Object#toString()} method will be called on {@code value} to determine
   * the String value to actually use for the header value.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest putHeader(String key, Object value);

  /**
   * Inserts all the specified headers into the request. The {@link Object#toString()} method will be called on the header values {@code value} to determine
   * the String value to actually use for the header value.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest putAllHeaders(Map<String, ? extends Object> m);

  /**
   * Write a {@link Buffer} to the request body.
   */
  public abstract void writeBuffer(Buffer chunk);

  /**
   * Write a {@link Buffer} to the request body.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest write(Buffer chunk);

  /**
   * Write a {@link String} to the request body, encoded in UTF-8.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest write(String chunk);

  /**
   * Write a {@link String} to the request body, encoded using the encoding {@code enc}.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest write(String chunk, String enc);

  /**
   * Write a {@link Buffer} to the request body. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest write(Buffer chunk, Handler<Void> doneHandler);

  /**
   * Write a {@link String} to the request body, encoded in UTF-8. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest write(String chunk, Handler<Void> doneHandler);

  /**
   * Write a {@link String} to the request body, encoded with encoding {@code enc}. The {@code doneHandler} is called after the buffer is actually written to the wire.<p>
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest write(String chunk, String enc, Handler<Void> doneHandler);

  /**
   * Data is queued until it is actually sent. To set the point at which the queue is considered "full" call this method
   * specifying the {@code maxSize} in bytes.<p>
   * This method is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   */
  public abstract void setWriteQueueMaxSize(int maxSize);
  /**
   * If the amount of data that is currently queued is greater than the write queue max size see {@link #setWriteQueueMaxSize(int)}
   * then the request queue is considered full.<p>
   * Data can still be written to the request even if the write queue is deemed full, however it should be used as indicator
   * to stop writing and push back on the source of the data, otherwise you risk running out of available RAM.<p>
   * This method is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams and perform flow control.
   *
   * @return {@code true} if the write queue is full, {@code false} otherwise
   */
  public abstract boolean writeQueueFull();

  /**
   * This method sets a drain handler {@code handler} on the request. The drain handler will be called when write queue is no longer
   * full and it is safe to write to it again.<p>
   * The drain handler is actually called when the write queue size reaches <b>half</b> the write queue max size to prevent thrashing.
   * This method is used as part of a flow control strategy, e.g. it is used by the {@link org.vertx.java.core.streams.Pump} class to pump data
   * between different streams.
   *
   * @param handler
   */
  public abstract void drainHandler(Handler<Void> handler);

  /**
   * Set {@code handler} as an exception handler on the request. Any exceptions that occur, either at connection setup time or later
   * will be notified by calling the handler. If the request has no handler than any exceptions occurring will be
   * output to {@link System#err}
   */
  public abstract void exceptionHandler(Handler<Exception> handler);

  /**
   * If you send an HTTP request with the header {@code Expect} set to the value {@code 100-continue}
   * and the server responds with an interim HTTP response with a status code of {@code 100} and a continue handler
   * has been set using this method, then the {@code handler} will be called.<p>
   * You can then continue to write data to the request body and later end it. This is normally used in conjunction with
   * the {@link #sendHead()} method to force the request header to be written before the request has ended.
   */
  public abstract void continueHandler(Handler<Void> handler);

  /**
   * Forces the head of the request to be written before {@link #end()} is called on the request. This is normally used
   * to implement HTTP 100-continue handling, see {@link #continueHandler(org.vertx.java.core.Handler)} for more information.
   *
   * @return A reference to this, so multiple method calls can be chained.
   */
  public abstract HttpClientRequest sendHead();

  /**
   * Same as {@link #end(Buffer)} but writes a String with the default encoding
   */
  public abstract void end(String chunk);

  /**
   * Same as {@link #end(Buffer)} but writes a String with the specified encoding
   */
  public abstract void end(String chunk, String enc);

  /**
   * Same as {@link #end()} but writes some data to the request body before ending. If the request is not chunked and
   * no other data has been written then the Content-Length header will be automatically set
   */
  public abstract void end(Buffer chunk);

  /**
   * Ends the request. If no data has been written to the request body, and {@link #sendHead()} has not been called then
   * the actual request won't get written until this method gets called.<p>
   * Once the request has ended, it cannot be used any more, and if keep alive is true the underlying connection will
   * be returned to the {@link HttpClient} pool so it can be assigned to another request.
   */
  public abstract void end();

}
