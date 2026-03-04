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
package io.vertx.it.networklogging;

import io.vertx.test.http.HttpConfig;
import io.vertx.test.netty.TestLoggerFactory;

public class Http1Test extends HttpTestBase {

  public Http1Test() {
    super(HttpConfig.Http1x.DEFAULT);
  }

  @Override
  protected boolean check(TestLoggerFactory factory) {
    return factory.hasName("io.netty.handler.logging.LoggingHandler");
  }
}
