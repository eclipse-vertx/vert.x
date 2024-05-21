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
package io.vertx.core.streams;

import io.vertx.core.Future;
import io.vertx.test.core.AsyncTestBase;
import io.vertx.test.fakestream.FakeStream;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ReadStreamReduceTest extends AsyncTestBase {

  private FakeStream<Object> dst;
  private Object o1 = new Object();
  private Object o2 = new Object();
  private Object o3 = new Object();

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    dst = new FakeStream<>();
  }

  @Test
  public void testCollect() {
    Future<List<Object>> list = dst.collect(Collectors.toList());
    assertFalse(list.isComplete());
    dst.write(o1);
    assertFalse(list.isComplete());
    dst.write(o2);
    assertFalse(list.isComplete());
    dst.write(o3);
    dst.end();
    assertTrue(list.succeeded());
    assertEquals(Arrays.asList(o1, o2, o3), list.result());
  }

  @Test
  public void testFailure() {
    Future<List<Object>> list = dst.collect(Collectors.toList());
    assertFalse(list.isComplete());
    dst.write(o1);
    assertFalse(list.isComplete());
    dst.write(o2);
    assertFalse(list.isComplete());
    Throwable err = new Throwable();
    dst.fail(err);
    assertTrue(list.failed());
    assertSame(err, list.cause());
  }
}
