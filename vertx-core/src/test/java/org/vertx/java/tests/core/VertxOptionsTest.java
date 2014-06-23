/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.tests.core;

import org.junit.Test;
import org.vertx.java.core.VertxOptions;
import static org.vertx.java.tests.core.TestUtils.*;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxOptionsTest extends VertxTestBase {

  @Test
  public void testOptions() {
    VertxOptions options = new VertxOptions();
    assertEquals(2 * Runtime.getRuntime().availableProcessors(), options.getEventLoopPoolSize());
    int rand = randomPositiveInt();
    assertEquals(options, options.setEventLoopPoolSize(rand));
    assertEquals(rand, options.getEventLoopPoolSize());
    try {
      options.setEventLoopPoolSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals(20, options.getWorkerPoolSize());
    rand = randomPositiveInt();
    assertEquals(options, options.setWorkerPoolSize(rand));
    assertEquals(rand, options.getWorkerPoolSize());
    try {
      options.setWorkerPoolSize(0);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertFalse(options.isClustered());
    assertEquals(options, options.setClustered(true));
    assertTrue(options.isClustered());
    assertEquals(0, options.getClusterPort());
    assertEquals(options, options.setClusterPort(1234));
    assertEquals(1234, options.getClusterPort());
    try {
      options.setClusterPort(-1);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    try {
      options.setClusterPort(65536);
      fail("Should throw exception");
    } catch (IllegalArgumentException e) {
      // OK
    }
    assertEquals("localhost", options.getClusterHost());
    String randString = randomUnicodeString(100);
    assertEquals(options, options.setClusterHost(randString));
    assertEquals(randString, options.getClusterHost());
  }
}
