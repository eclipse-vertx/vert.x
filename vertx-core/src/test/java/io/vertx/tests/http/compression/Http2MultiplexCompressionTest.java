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

package io.vertx.tests.http.compression;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.test.http.HttpTestBase;
import io.vertx.tests.http.Http2TestBase;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2MultiplexCompressionTest extends Http2CompressionTest {

  public Http2MultiplexCompressionTest(int compressionLevel) {
    super(compressionLevel);
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return super.createBaseServerOptions().setHttp2MultiplexImplementation(true);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return super.createBaseClientOptions(); // Todo
  }
}
