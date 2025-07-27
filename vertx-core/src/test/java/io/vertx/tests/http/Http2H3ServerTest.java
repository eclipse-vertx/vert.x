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

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 */
public class Http2H3ServerTest extends HttpServerTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory.createH3HttpServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createH3HttpClientOptions().setDefaultPort(DEFAULT_HTTPS_PORT).setDefaultHost(DEFAULT_HTTPS_HOST);
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
}
