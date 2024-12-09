/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.http;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;

import java.util.concurrent.TimeUnit;

import static io.vertx.tests.http.HttpOptionsFactory.*;

public class Http2ServerTest extends HttpServerTest {

  @Override
  public void setUp() throws Exception {
    eventLoopGroups.clear();
    serverOptions =  createHttp2ServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
    clientOptions = createHttp2ClientOptions();
    super.setUp();
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return serverOptions;
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return clientOptions;
  }
}
