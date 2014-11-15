/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import org.junit.Test;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class CreateVertxTest extends AsyncTestBase {

  @Test
  public void testCreateSimpleVertx() {
    Vertx vertx = Vertx.vertx();
    assertNotNull(vertx);
  }

  @Test
  public void testCreateVertxWithOptions() {
    VertxOptions options = new VertxOptions();
    Vertx vertx = Vertx.vertx(options);
    assertNotNull(vertx);
  }

  @Test
  public void testFailCreateClusteredVertxSynchronously() {
    VertxOptions options = new VertxOptions();
    options.setClustered(true);
    try {
      Vertx.vertx(options);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
  }

  @Test
  public void testCreateClusteredVertxAsync() {
    VertxOptions options = new VertxOptions();
    options.setClustered(true);
    Vertx.vertxAsync(options, ar -> {
      assertTrue(ar.succeeded());
      assertNotNull(ar.result());
      testComplete();
    });
    await();
  }

  @Test
  public void testCreateNonClusteredVertxAsync() {
    VertxOptions options = new VertxOptions();
    Vertx.vertxAsync(options, ar -> {
      assertTrue(ar.succeeded());
      assertNotNull(ar.result());
      testComplete();
    });
    await();
  }
}
