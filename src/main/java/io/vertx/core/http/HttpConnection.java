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

/**
 * Represents an HTTP connection.<p/>
 *
 * todo:
 *
 * - HttpServerResponse illegal state exception in invalid state
 * - add a HttpServer.connectionHandler(Handler<HttpServerConnection>) to allow to set the connection initial settings
 * - server synchronization + executeFromIO
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpConnection {

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

}
