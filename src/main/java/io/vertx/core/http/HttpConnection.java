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
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;

/**
 * Represents an HTTP connection.<p/>
 *
 * todo:
 *
 * - response writeContinue()
 * - add a HttpServer.connectionHandler(Handler<HttpServerConnection>) to allow to set the connection initial settings
 * - server synchronization + executeFromIO
 * - metrics
 * - byte distribution algorithm configurability (options ? connection ?)
 * - h2c
 * - http upgrade
 * - HttpClient
 * - remove okio tests
 *
 * not yet in scope:
 * - stream priority
 * - unknown frames (send / receive)
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
    return goAway(errorCode, 2^31 - 1);
  }

  /**
   * Like {@link #goAway(long, int, Buffer)} with no buffer.
   */
  @Fluent
  default HttpConnection goAway(long errorCode, int lastStreamId) {
    return goAway(errorCode, lastStreamId, null);
  }

  /**
   * Like {@link #goAway(long, int, Buffer, Handler)}.
   */
  @Fluent
  default HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData) {
    return goAway(errorCode, lastStreamId, debugData, null);
  }

  /**
   * Send a go away frame to the client.<p/>
   *
   * <ul>
   *   <li>a {@literal GOAWAY} frame is sent to the to the client with the {@code errorCode} and {@@code debugData}</li>
   *   <li>any stream created after the stream identified by {@code lastStreamId} will be closed</li>
   *   <li>for an {@literal errorCode} is different than {@literal 0} when all the remaining streams are closed this connection will be closed automatically</li>
   * </ul>
   *
   * @param errorCode the {@literal GOAWAY} error code
   * @param lastStreamId the last stream id
   * @param debugData additional debug data sent to the client
   * @param completionHandler the handler notified when all stream have been closed
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection goAway(long errorCode, int lastStreamId, Buffer debugData, Handler<Void> completionHandler);

  /**
   * Initiate a connection shutdown, a go away frame is sent and the connection is closed when all current streams
   * will be closed.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection shutdown();

  /**
   * Initiate a connection shutdown, a go away frame is sent and the connection is closed when all current streams
   * will be closed or the {@code timeout} is fired.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection shutdown(long timeout);

  /**
   * Set a close handler. The handler will get notified when the connection is closed.
   *
   * @param handler the handler to be notified
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection closeHandler(Handler<Void> handler);

  /**
   * @return the latest server settings acknowledged by the client
   */
  Http2Settings settings();

  /**
   * Send to the client an update of the server settings.
   *
   * @param settings the new settings
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection updateSettings(Http2Settings settings);

  /**
   * Send to the client an update of the server settings.<p/>
   *
   * The {@code completionHandler} will be notified when the client has acknowledged the settings.
   *
   * @param settings the new settings
   * @param completionHandler the handler notified when the settings have been acknowledged by the client
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection updateSettings(Http2Settings settings, Handler<AsyncResult<Void>> completionHandler);

  /**
   * @return the current client settings for this connection
   */
  Http2Settings clientSettings();

  /**
   * Set an handler that is called when client {@link Http2Settings} are updated.
   *
   * @param handler the handler for client settings
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpConnection clientSettingsHandler(Handler<Http2Settings> handler);

  /**
   * @return the handler for client settings
   */
  @GenIgnore
  Handler<Http2Settings> clientSettingsHandler();

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
