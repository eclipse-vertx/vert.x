/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Represents an HTTP/2 connection.<p/>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpConnection {

  /**
   * Like {@link #goAway(long, int)} with a last stream id {@code 2^31-1}.
   */
  @Fluent
  default HttpConnection goAway(long errorCode) {
    return goAway(errorCode, Integer.MAX_VALUE);
  }

  /**
   * Like {@link #goAway(long, int, Buffer)} with no buffer.
   */
  @Fluent
  default HttpConnection goAway(long errorCode, int lastStreamId) {
    return goAway(errorCode, lastStreamId, null);
  }

  /**
   * Send a go away frame to the remote endpoint of the connection.<p/>
   *
   * <ul>
   *   <li>a {@literal GOAWAY} frame is sent to the to the remote endpoint with the {@code errorCode} and {@@code debugData}</li>
   *   <li>any stream created after the stream identified by {@code lastStreamId} will be closed</li>
   *   <li>for an {@literal errorCode} is different than {@literal 0} when all the remaining streams are closed this connection will be closed automatically</li>
   * </ul>
   *
   * @param errorCode the {@literal GOAWAY} error code
   * @param lastStreamId the last stream id
   * @param debugData additional debug data sent to the remote endpoint
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData);

  /**
   * Set an handler called when a {@literal GOAWAY} frame is received.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection goAwayHandler(@Nullable Handler<GoAway> handler);

  /**
   * Set an handler called when a {@literal GOAWAY} frame has been sent or received and all connections are closed.
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection shutdownHandler(@Nullable  Handler<Void> handler);

  /**
   * Initiate a connection shutdown, a go away frame is sent and the connection is closed when all current active streams
   * are closed or after a time out of 30 seconds.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection shutdown();

  /**
   * Initiate a connection shutdown, a go away frame is sent and the connection is closed when all current streams
   * will be closed or the {@code timeout} is fired.
   *
   * @param timeoutMs the timeout in milliseconds
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection shutdown(long timeoutMs);

  /**
   * Set a close handler. The handler will get notified when the connection is closed.
   *
   * @param handler the handler to be notified
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection closeHandler(Handler<Void> handler);

  /**
   * Close the connection and all the currently active streams. A {@literal GOAWAY} frame will be sent before.<p/>
   */
  void close();

  /**
   * @return the latest server settings acknowledged by the remote endpoint
   */
  Http2Settings settings();

  /**
   * Send to the remote endpoint an update of the server settings.
   *
   * @param settings the new settings
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection updateSettings(Http2Settings settings);

  /**
   * Send to the remote endpoint an update of this endpoint settings.<p/>
   *
   * The {@code completionHandler} will be notified when the remote endpoint has acknowledged the settings.
   *
   * @param settings the new settings
   * @param completionHandler the handler notified when the settings have been acknowledged by the remote endpoint
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler);

  /**
   * @return the current remote endpoint settings for this connection
   */
  Http2Settings remoteSettings();

  /**
   * Set an handler that is called when remote endpoint {@link Http2Settings} are updated.
   *
   * @param handler the handler for remote endpoint settings
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection remoteSettingsHandler(Handler<Http2Settings> handler);

  /**
   * @return the handler for remote endpoint settings
   */
  @GenIgnore
  Handler<Http2Settings> remoteSettingsHandler();

  /**
   * Send a {@literal PING} frame to the remote endpoint.
   *
   * @param data the 8 bytes data of the frame
   * @param pongHandler an async result handler notified with pong reply or the failure
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection ping(Buffer data, Handler<AsyncResult<Buffer>> pongHandler);

  /**
   * Set an handler notified when a {@literal PING} frame is received from the remote endpoint.
   *
   * @param handler the handler to be called when a {@literal PING} is received
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection pingHandler(@Nullable Handler<Buffer> handler);

  /**
   * Set an handler called when a connection error happens
   *
   * @param handler the handler
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection exceptionHandler(Handler<Throwable> handler);

  /**
   * @return the handler for exceptions
   */
  @GenIgnore
  Handler<Throwable> exceptionHandler();
}
