/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:victorqrsilva@gmail.com">Victor Quezado</a>
 */
public class HttpMethodTest {

  // IS SAFE?
  @Test
  public void testOPTIONSIsSafe() {
    assertEquals(HttpMethod.OPTIONS.isSafe(), false);
  }

  @Test
  public void testGETIsSafe() {
    assertEquals(HttpMethod.GET.isSafe(), true);
  }

  @Test
  public void testHEADIsSafe() {
    assertEquals(HttpMethod.HEAD.isSafe(), true);
  }

  @Test
  public void testPOSTIsSafe() {
    assertEquals(HttpMethod.POST.isSafe(), false);
  }

  @Test
  public void testPUTIsSafe() {
    assertEquals(HttpMethod.PUT.isSafe(), false);
  }

  @Test
  public void testDELETEIsSafe() {
    assertEquals(HttpMethod.DELETE.isSafe(), false);
  }

  @Test
  public void testTRACEIsSafe() {
    assertEquals(HttpMethod.TRACE.isSafe(), false);
  }

  @Test
  public void testCONNECTIsSafe() {
    assertEquals(HttpMethod.CONNECT.isSafe(), false);
  }

  @Test
  public void testPATCHIsSafe() {
    assertEquals(HttpMethod.PATCH.isSafe(), false);
  }

  @Test
  public void testOTHERIsSafe() {
    assertEquals(HttpMethod.OTHER.isSafe(), false);
  }

  // IS IDEMPOTENT?
  @Test
  public void testOPTIONSIsIdempotent() {
    assertEquals(HttpMethod.OPTIONS.isIdempotent(), false);
  }

  @Test
  public void testGETIsIdempotent() {
    assertEquals(HttpMethod.GET.isIdempotent(), true);
  }

  @Test
  public void testHEADIsIdempotent() {
    assertEquals(HttpMethod.HEAD.isIdempotent(), true);
  }

  @Test
  public void testPOSTIsIdempotent() {
    assertEquals(HttpMethod.POST.isIdempotent(), false);
  }

  @Test
  public void testPUTIsIdempotent() {
    assertEquals(HttpMethod.PUT.isIdempotent(), true);
  }

  @Test
  public void testDELETEIsIdempotent() {
    assertEquals(HttpMethod.DELETE.isIdempotent(), true);
  }

  @Test
  public void testTRACEIsIdempotent() {
    assertEquals(HttpMethod.TRACE.isIdempotent(), false);
  }

  @Test
  public void testCONNECTIsIdempotent() {
    assertEquals(HttpMethod.CONNECT.isIdempotent(), false);
  }

  @Test
  public void testPATCHIsIdempotent() {
    assertEquals(HttpMethod.PATCH.isIdempotent(), true);
  }

  @Test
  public void testOTHERIsIdempotent() {
    assertEquals(HttpMethod.OTHER.isIdempotent(), false);
  }
}
