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
package io.vertx.tests.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.internal.VertxInternal;
import io.vertx.test.core.VertxTestBase2;
import io.vertx.test.fakemetrics.FakeVertxMetrics;
import io.vertx.test.faketracer.FakeTracer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VertxBuilderTest extends VertxTestBase2 {

  @Test
  public void testTracerFactoryDoesNotRequireOptions() {
    FakeTracer tracer = new FakeTracer();
    Vertx vertx = Vertx.builder().withTracer(options -> tracer).build();
    try {
      assertEquals(tracer, ((VertxInternal)vertx).tracer());
    } finally {
      vertx.close().await();
    }
  }

  @Test
  public void testMetricsFactoryDoesNotRequireOptions() {
    FakeVertxMetrics metrics = new FakeVertxMetrics();
    Vertx vertx = Vertx.builder().withMetrics(options -> metrics).build();
    try {
      assertEquals(metrics, ((VertxInternal)vertx).metrics());
    } finally {
      vertx.close().await();
    }
  }
}
