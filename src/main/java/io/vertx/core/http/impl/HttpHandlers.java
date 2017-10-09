/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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

  final Handler<HttpServerRequest> requesthHandler;
  final Handler<ServerWebSocket> wsHandler;
  final Handler<HttpConnection> connectionHandler;
  final Handler<Throwable> exceptionHandler;

  public HttpHandlers(
    Handler<HttpServerRequest> requesthHandler,
    Handler<ServerWebSocket> wsHandler,
    Handler<HttpConnection> connectionHandler,
    Handler<Throwable> exceptionHandler) {
    this.requesthHandler = requesthHandler;
    this.wsHandler = wsHandler;
    this.connectionHandler = connectionHandler;
    this.exceptionHandler = exceptionHandler;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    HttpHandlers that = (HttpHandlers) o;

    if (!Objects.equals(requesthHandler, that.requesthHandler)) return false;
    if (!Objects.equals(wsHandler, that.wsHandler)) return false;
    if (!Objects.equals(connectionHandler, that.connectionHandler)) return false;
    if (!Objects.equals(exceptionHandler, that.exceptionHandler)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = 0;
    if (requesthHandler != null) {
      result = 31 * result + requesthHandler.hashCode();
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
