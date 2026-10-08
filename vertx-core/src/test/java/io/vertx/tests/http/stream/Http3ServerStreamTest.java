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
package io.vertx.tests.http.stream;

import io.vertx.test.core.Checkpoint;
import io.vertx.tests.http.http3.Http3Configurator;
import org.junit.Ignore;
import org.junit.Test;

public class Http3ServerStreamTest extends HttpServerStreamTest {

  public Http3ServerStreamTest() {
    super(Http3Configurator.INSTANCE);
  }

  @Test
  @Ignore("Cannot really pass at the moment")
  @Override
  public void testHandleCloseClosingConnection1(Checkpoint checkpoint1, Checkpoint checkpoint2) throws Exception {
    super.testHandleCloseClosingConnection1(checkpoint1, checkpoint2);
  }
}
