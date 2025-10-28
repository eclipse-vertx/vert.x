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
package io.vertx.tests.http.http3;

import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.http.*;
import io.vertx.test.core.TestUtils;
import io.vertx.test.fakemetrics.*;
import io.vertx.test.http.HttpTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.tls.Trust;
import io.vertx.tests.http.Http2TestBase;
import io.vertx.tests.metrics.HttpMetricsTestBase;
import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

public class Http3MetricsTest extends HttpMetricsTestBase {

  public Http3MetricsTest() {
    super(Http3Config.INSTANCE, HttpVersion.HTTP_3, ThreadingModel.EVENT_LOOP);
  }

  @Ignore
  @Test
  public void testRouteMetricsIgnoredAfterResponseEnd() throws Exception {
  }
}
