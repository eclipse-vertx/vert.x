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

package org.vertx.java.platform.impl.resolver.requesters;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpClientResponse;

import java.util.HashMap;
import java.util.Map;

public class HttpHandlers {

  private final Map<Integer, Handler<HttpClientResponse>> handlers;

  public HttpHandlers() {
    this.handlers = new HashMap<>();
  }

  public HttpHandlers(Map<Integer, Handler<HttpClientResponse>> handlers) {
    this();
    this.handlers.putAll(handlers);
  }

  public HttpHandlers(HttpHandlers httpHandlers) {
    this(httpHandlers.handlers);
  }

  public void addHandler(int statusCode, Handler<HttpClientResponse> handler) {
    handlers.put(statusCode, handler);
  }

  public void removeHandlersWithStatusCode(int statusCode) {
    handlers.remove(statusCode);
  }

  // Returns true if there is an handle to process the status code of the reply. False otherwise.
  public boolean handle(HttpClientResponse resp) {
    Handler<HttpClientResponse> handler = handlers.get(resp.statusCode());
    if (handler == null) {
      return false;
    } else {
      handler.handle(resp);
      return true;
    }
  }
}
