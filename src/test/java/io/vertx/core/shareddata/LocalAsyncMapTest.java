/*
 * Copyright (c) 2011-2018 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata;

import io.vertx.core.Vertx;
import io.vertx.test.core.Repeat;
import org.junit.Test;

/**
 * @author Thomas Segismont
 */
public class LocalAsyncMapTest extends AsyncMapTest {

  @Override
  protected Vertx getVertx() {
    return vertx;
  }

  @Test
  @Repeat(times = 100)
  @Override
  public void testMapPutTtl() {
    super.testMapPutTtl();
  }

  @Test
  @Repeat(times = 100)
  @Override
  public void testMapPutTtlThenPut() {
    super.testMapPutTtlThenPut();
  }

  @Test
  @Repeat(times = 100)
  @Override
  public void testMapPutIfAbsentTtl() {
    super.testMapPutIfAbsentTtl();
  }
}
