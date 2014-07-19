/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.ext.sockjs;

import io.vertx.core.Handler;
import io.vertx.core.ServiceHelper;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.sockjs.impl.EventBusBridgeHook;
import io.vertx.ext.sockjs.impl.SockJSServerImpl;
import io.vertx.ext.sockjs.impl.SockJSSocket;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public interface SockJSServer {

  static SockJSServer newSockJSServer(Vertx vertx, HttpServer httpServer) {
    return factory.newSockJSServer(vertx, httpServer);
  }

  SockJSServerImpl setHook(EventBusBridgeHook hook);

  SockJSServerImpl installApp(JsonObject config,
                                 final Handler<SockJSSocket> sockHandler);

  SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted);

  SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                             long authTimeout);

  SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                             long authTimeout, String authAddress);

  SockJSServerImpl bridge(JsonObject sjsConfig, JsonArray inboundPermitted, JsonArray outboundPermitted,
                             JsonObject bridgeConfig);

  void close();

  /*
  These applications are required by the SockJS protocol and QUnit tests
   */
  void installTestApplications();

  static final SockJSServerFactory factory = ServiceHelper.loadFactory(SockJSServerFactory.class);

}
