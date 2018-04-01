/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.vertx.core.Handler;
import io.vertx.core.http.HttpConnection;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.ServerWebSocket;

import java.util.Objects;

/**
 * HTTP server handlers.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class HttpHandlers {

  final Handler<HttpServerRequest> requestHandler;
  final Handler<ServerWebSocket> wsHandler;
  final Handler<HttpConnection> connectionHandler;
  final Handler<Throwable> exceptionHandler;

  public HttpHandlers(
    Handler<HttpServerRequest> requestHandler,
    Handler<ServerWebSocket> wsHandler,
    Handler<HttpConnection> connectionHandler,
    Handler<Throwable> exceptionHandler) {
    this.requestHandler = requestHandler;
    this.wsHandler = wsHandler;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HttpHandlers that = (HttpHandlers) o;

    if (!Objects.equals(requestHandler, that.requestHandler)) return false;
    if (!Objects.equals(wsHandler, that.wsHandler)) return false;
    if (!Objects.equals(connectionHandler, that.connectionHandler)) return false;
    if (!Objects.equals(exceptionHandler, that.exceptionHandler)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    if (requestHandler != null) {
      result = 31 * result + requestHandler.hashCode();
    }
    if (wsHandler != null) {
      result = 31 * result + wsHandler.hashCode();
    }
    if (connectionHandler != null) {
      result = 31 * result + connectionHandler.hashCode();
    }
    if (exceptionHandler != null) {
      result = 31 * result + exceptionHandler.hashCode();
    }
    return result;
  }
}
