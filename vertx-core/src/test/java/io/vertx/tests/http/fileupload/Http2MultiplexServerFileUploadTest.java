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
package io.vertx.tests.http.fileupload;

import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.tests.http.Http2TestBase;

/**
 */
public class Http2MultiplexServerFileUploadTest extends Http2ServerFileUploadTest {

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return super.createBaseServerOptions().setHttp2MultiplexImplementation(true);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return super.createBaseClientOptions().setHttp2MultiplexImplementation(true);
  }
}
