/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.connection;

import io.vertx.core.http.Http3Settings;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.tests.http.Http3TestBase;

public class Http3ClientConnectionTest extends HttpClientConnectionTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return Http3TestBase.createHttp3ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST)
      .setInitialHttp3Settings(new Http3Settings());
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return Http3TestBase.createHttp3ClientOptions();
  }
}