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
import io.vertx.core.http.*;
import io.vertx.test.fakemetrics.*;
import io.vertx.tests.metrics.HttpMetricsTestBase;
import org.junit.Ignore;
import org.junit.Test;


public class Http3MetricsTest extends HttpMetricsTestBase {

  public Http3MetricsTest() {
    super(Http3Config.INSTANCE, HttpVersion.HTTP_3, ThreadingModel.EVENT_LOOP);
  }

  @Ignore
  @Test
  public void testRouteMetricsIgnoredAfterResponseEnd() throws Exception {
  }
}
