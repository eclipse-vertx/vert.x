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
package io.vertx.tests.net;

import io.vertx.core.internal.net.RFC3986;
import org.junit.ComparisonFailure;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RFC3986Test {

  @Test
  public void testRemoveDotSegmentsRuleA() {
    assertDotSegments("", "../");
    assertDotSegments("", "./");

    assertDotSegments("foo", "../foo");
    assertDotSegments("foo", "./foo");
  }

  @Test
  public void testRemoveDotSegmentsRuleB() {
    assertDotSegments("/", "/./");
    assertDotSegments("/", "/.");

    assertDotSegments("/foo", "/./foo");
  }

  @Test
  public void testRemoveDotSegmentsRuleC() {
    assertDotSegments("/", "/../");
    assertDotSegments("/foo", "/../foo");
    assertDotSegments("/", "/..");
    assertDotSegments("/", "/foo/../");
    assertDotSegments("/", "/foo/..");
    assertDotSegments("/", "foo/../");
    assertDotSegments("/", "foo/..");
    assertDotSegments("/foo/", "/foo/bar/../");
    assertDotSegments("/foo/", "/foo/bar/..");
    assertDotSegments("foo/", "foo/bar/../");
    assertDotSegments("foo/", "foo/bar/..");
  }

  @Test
  public void testRemoveDotSegmentsRuleD() {
    assertDotSegments("", ".");
    assertDotSegments("", "..");
  }

  @Test
  public void testRemoveDotSegmentsRuleE() {
    assertDotSegments("/foo", "/foo");
    assertDotSegments("foo", "foo");
  }

  private static void assertDotSegments(String expected, String test) {
    String actual = RFC3986.removeDotSegments(test);
    assertEquals(expected, actual);
  }
}
