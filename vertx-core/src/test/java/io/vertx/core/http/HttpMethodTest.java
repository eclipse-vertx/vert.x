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
package io.vertx.core.http;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

public class HttpMethodTest {

  @Test
  public void testConstantNames() {
    assertEquals("GET", HttpMethod.GET.name());
    assertEquals("POST", HttpMethod.POST.name());
    assertEquals("PUT", HttpMethod.PUT.name());
    assertEquals("HEAD", HttpMethod.HEAD.name());
    assertEquals("CONNECT", HttpMethod.CONNECT.name());
    assertEquals("DELETE", HttpMethod.DELETE.name());
    assertEquals("OPTIONS", HttpMethod.OPTIONS.name());
    assertEquals("PATCH", HttpMethod.PATCH.name());
    assertEquals("TRACE", HttpMethod.TRACE.name());
    assertEquals("PROPFIND", HttpMethod.PROPFIND.name());
    assertEquals("PROPPATCH", HttpMethod.PROPPATCH.name());
    assertEquals("MKCOL", HttpMethod.MKCOL.name());
    assertEquals("COPY", HttpMethod.COPY.name());
    assertEquals("MOVE", HttpMethod.MOVE.name());
    assertEquals("LOCK", HttpMethod.LOCK.name());
    assertEquals("UNLOCK", HttpMethod.UNLOCK.name());
    assertEquals("MKCALENDAR", HttpMethod.MKCALENDAR.name());
    assertEquals("VERSION-CONTROL", HttpMethod.VERSION_CONTROL.name());
    assertEquals("REPORT", HttpMethod.REPORT.name());
    assertEquals("CHECKOUT", HttpMethod.CHECKOUT.name());
    assertEquals("CHECKIN", HttpMethod.CHECKIN.name());
    assertEquals("UNCHECKOUT", HttpMethod.UNCHECKOUT.name());
    assertEquals("MKWORKSPACE", HttpMethod.MKWORKSPACE.name());
    assertEquals("UPDATE", HttpMethod.UPDATE.name());
    assertEquals("LABEL", HttpMethod.LABEL.name());
    assertEquals("MERGE", HttpMethod.MERGE.name());
    assertEquals("BASELINE-CONTROL", HttpMethod.BASELINE_CONTROL.name());
    assertEquals("MKACTIVITY", HttpMethod.MKACTIVITY.name());
    assertEquals("ORDERPATCH", HttpMethod.ORDERPATCH.name());
    assertEquals("ACL", HttpMethod.ACL.name());
    assertEquals("SEARCH", HttpMethod.SEARCH.name());
  }

  @Test
  public void testConstants() {
    for (HttpMethod method : Arrays.asList(
      HttpMethod.GET,
      HttpMethod.POST,
      HttpMethod.HEAD,
      HttpMethod.PUT,
      HttpMethod.CONNECT,
      HttpMethod.DELETE,
      HttpMethod.OPTIONS,
      HttpMethod.PATCH,
      HttpMethod.TRACE,
      HttpMethod.PROPFIND,
      HttpMethod.PROPPATCH,
      HttpMethod.MKCOL,
      HttpMethod.COPY,
      HttpMethod.MOVE,
      HttpMethod.LOCK,
      HttpMethod.UNLOCK,
      HttpMethod.MKCALENDAR,
      HttpMethod.VERSION_CONTROL,
      HttpMethod.REPORT,
      HttpMethod.CHECKOUT,
      HttpMethod.CHECKIN,
      HttpMethod.UNCHECKOUT,
      HttpMethod.MKWORKSPACE,
      HttpMethod.UPDATE,
      HttpMethod.LABEL,
      HttpMethod.MERGE,
      HttpMethod.BASELINE_CONTROL,
      HttpMethod.MKACTIVITY,
      HttpMethod.ORDERPATCH,
      HttpMethod.ACL,
      HttpMethod.SEARCH
      )) {
      assertSame(HttpMethod.valueOf(method.name()), method);
      assertSame(method.name(), method.toString());
    }
  }

  @Test
  public void testInvalidValueOf() {
    for (String method : Arrays.asList("", " ")) {
      try {
        HttpMethod.valueOf(method);
        fail();
      } catch (IllegalArgumentException ignore) {
      }
    }
    try {
      HttpMethod.valueOf(null);
      fail();
    } catch (NullPointerException ignore) {
    }
  }

  @Test
  public void testValueOf() {
    HttpMethod m1 = HttpMethod.valueOf("foo");
    HttpMethod m2 = HttpMethod.valueOf("foo");
    assertEquals("foo", m1.name());
    assertEquals("foo", m1.toString());
    assertNotSame(m1, m2);
    assertEquals(m1.hashCode(), m2.hashCode());
    assertEquals(m1, m2);
  }

  @Test
  public void testCaseSensitive() {
    HttpMethod m1 = HttpMethod.valueOf("Foo");
    HttpMethod m2 = HttpMethod.valueOf("foo");
    assertEquals("Foo", m1.name());
    assertEquals("Foo", m1.toString());
    assertNotSame(m1, m2);
    assertNotEquals(m1.hashCode(), m2.hashCode());
    assertNotEquals(m1, m2);
  }

  @Test
  public void testNettyInterop() {
    assertSame(HttpMethod.GET.toNetty(), io.netty.handler.codec.http.HttpMethod.GET);
    assertSame(HttpMethod.POST.toNetty(), io.netty.handler.codec.http.HttpMethod.POST);
    assertSame(HttpMethod.PUT.toNetty(), io.netty.handler.codec.http.HttpMethod.PUT);
    assertSame(HttpMethod.HEAD.toNetty(), io.netty.handler.codec.http.HttpMethod.HEAD);
    assertSame(HttpMethod.CONNECT.toNetty(), io.netty.handler.codec.http.HttpMethod.CONNECT);
    assertSame(HttpMethod.DELETE.toNetty(), io.netty.handler.codec.http.HttpMethod.DELETE);
    assertSame(HttpMethod.OPTIONS.toNetty(), io.netty.handler.codec.http.HttpMethod.OPTIONS);
    assertSame(HttpMethod.PATCH.toNetty(), io.netty.handler.codec.http.HttpMethod.PATCH);
    assertSame(HttpMethod.TRACE.toNetty(), io.netty.handler.codec.http.HttpMethod.TRACE);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.GET), HttpMethod.GET);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.valueOf("GET")), HttpMethod.GET);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.POST), HttpMethod.POST);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.valueOf("POST")), HttpMethod.POST);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.PUT), HttpMethod.PUT);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.valueOf("PUT")), HttpMethod.PUT);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.HEAD), HttpMethod.HEAD);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.CONNECT), HttpMethod.CONNECT);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.DELETE), HttpMethod.DELETE);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.OPTIONS), HttpMethod.OPTIONS);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.PATCH), HttpMethod.PATCH);
    assertSame(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.TRACE), HttpMethod.TRACE);
    assertEquals(HttpMethod.valueOf("foo").toNetty().name(), "foo");
    assertEquals(HttpMethod.fromNetty(io.netty.handler.codec.http.HttpMethod.valueOf("foo")).name(), "foo");
  }

  @Test
  public void testValues() {
    List<HttpMethod> list = HttpMethod.values();
    assertTrue(!list.isEmpty());
    list.forEach(Assert::assertNotNull);
  }
}
