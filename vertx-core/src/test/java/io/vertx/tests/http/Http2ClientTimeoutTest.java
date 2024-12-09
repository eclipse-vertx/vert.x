/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import io.vertx.core.http.Http2Settings;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.http.HttpTestBase;

public class Http2ClientTimeoutTest extends HttpClientTimeoutTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return HttpOptionsFactory
      .createHttp2ServerOptions(HttpTestBase.DEFAULT_HTTPS_PORT, HttpTestBase.DEFAULT_HTTPS_HOST)
      .setInitialSettings(new Http2Settings().setMaxConcurrentStreams(5));
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return HttpOptionsFactory.createHttp2ClientOptions();
  }
}
