/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

import io.vertx.codegen.annotations.Fluent;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Handler;
import io.vertx.core.net.SSLEngineOptions;
import io.vertx.core.net.ServerSSLOptions;

/**
 * A builder for {@link HttpServer}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface HttpServerBuilder {

  /**
   * Configure the server.
   * @param config the server config
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerBuilder with(HttpServerConfig config);

  /**
   * Configure the server with the given SSL {@code options}.
   * @param options the SSL options
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerBuilder with(ServerSSLOptions options);

  /**
   * Configure the server with the given SSL {@code engine}.
   * @param engine the SSL engine options
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerBuilder with(SSLEngineOptions engine);

  /**
   * Set a connection handler for the server. This handler is called when a new connection is established.
   *
   * @return a reference to this, so the API can be used fluently
   */
  @Fluent
  HttpServerBuilder withConnectHandler(Handler<HttpConnection> handler);

  /**
   * Build and return the server.
   * @return the server as configured by this builder
   */
  HttpServer build();

}
