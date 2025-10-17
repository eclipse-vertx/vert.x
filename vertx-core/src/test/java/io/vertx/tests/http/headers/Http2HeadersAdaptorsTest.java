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

package io.vertx.tests.http.headers;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.spi.Http2HeadersMultiMap;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2HeadersAdaptorsTest extends HeadersTest {

  DefaultHttp2Headers headers;
  MultiMap map;

  @Before
  public void setUp() {
    headers = new DefaultHttp2Headers();
    map = new Http2HeadersMultiMap(headers);
  }

  @Override
  protected Http2HeadersMultiMap newMultiMap() {
    return new Http2HeadersMultiMap(new DefaultHttp2Headers());
  }

  @Test
  public void testGetConvertUpperCase() {
    map.set("foo", "foo_value");
    assertEquals("foo_value", map.get("Foo"));
    assertEquals("foo_value", map.get((CharSequence) "Foo"));
  }

  @Test
  public void testGetAllConvertUpperCase() {
    map.set("foo", "foo_value");
    assertEquals(Collections.singletonList("foo_value"), map.getAll("Foo"));
    assertEquals(Collections.singletonList("foo_value"), map.getAll((CharSequence) "Foo"));
  }

  @Test
  public void testContainsConvertUpperCase() {
    map.set("foo", "foo_value");
    assertTrue(map.contains("Foo"));
    assertTrue(map.contains((CharSequence) "Foo"));
  }

  @Test
  public void testSetConvertUpperCase() {
    map.set("Foo", "foo_value");
    map.set((CharSequence) "Bar", "bar_value");
    map.set("Juu", (Iterable<String>)Collections.singletonList("juu_value"));
    map.set("Daa", Collections.singletonList((CharSequence)"daa_value"));
    assertHeaderNames("foo","bar", "juu", "daa");
  }

  @Test
  public void testAddConvertUpperCase() {
    map.add("Foo", "foo_value");
    map.add((CharSequence) "Bar", "bar_value");
    map.add("Juu", (Iterable<String>)Collections.singletonList("juu_value"));
    map.add("Daa", Collections.singletonList((CharSequence)"daa_value"));
    assertHeaderNames("foo","bar", "juu", "daa");
  }

  @Test
  public void testRemoveConvertUpperCase() {
    map.set("foo", "foo_value");
    map.remove("Foo");
    map.set("bar", "bar_value");
    map.remove((CharSequence) "Bar");
    assertHeaderNames();
  }

  @Ignore
  @Test
  public void testEntries() {
    map.set("foo", Arrays.<String>asList("foo_value_1", "foo_value_2"));
    List<Map.Entry<String, String>> entries = map.entries();
    assertEquals(entries.size(), 1);
    assertEquals("foo", entries.get(0).getKey());
    assertEquals("foo_value_1", entries.get(0).getValue());
    map.set("bar", "bar_value");
    Map<String, String> collected = map.entries().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertEquals("foo_value_1", collected.get("foo"));
    assertEquals("bar_value", collected.get("bar"));
  }

  private void assertHeaderNames(String... expected) {
    assertEquals(new HashSet<>(Arrays.asList(expected)), headers.names().stream().map(CharSequence::toString).collect(Collectors.toSet()));
  }

  private Http2HeadersMultiMap headers(HttpMethod method, String authority, String host) {
    DefaultHttp2Headers s = new DefaultHttp2Headers();
    s.set(":method", method.name());
    if (method != HttpMethod.CONNECT) {
      s.set(":path", "/");
      s.set(":scheme", "http");
    }
    if (authority != null) {
      s.set(":authority", authority);
    }
    if (host != null) {
      s.set("host", host);
    }
    return new Http2HeadersMultiMap(s);
  }

  @Test
  public void testAuthorityValidation() {
    assertTrue(headers(HttpMethod.GET, null, null).validate(true));
    assertTrue(headers(HttpMethod.GET, "localhost:8080", null).validate(true));
    assertTrue(headers(HttpMethod.GET, null, "localhost:8080").validate(true));
    assertTrue(headers(HttpMethod.GET, "localhost:8080", "localhost:8080").validate(true));
    assertFalse(headers(HttpMethod.GET, "localhost:8080", "localhost:8081").validate(true));
    assertFalse(headers(HttpMethod.GET, "localhost:a", null).validate(true));
    assertFalse(headers(HttpMethod.GET, null, "localhost:a").validate(true));
    assertFalse(headers(HttpMethod.CONNECT, null, null).validate(true));
    assertTrue(headers(HttpMethod.CONNECT, "localhost:8080", null).validate(true));
    assertTrue(headers(HttpMethod.CONNECT, null, "localhost:8080").validate(true));
    assertTrue(headers(HttpMethod.CONNECT, "localhost:8080", "localhost:8080").validate(true));
    assertFalse(headers(HttpMethod.CONNECT, "localhost:8080", "localhost:8081").validate(true));
    assertFalse(headers(HttpMethod.CONNECT, "localhost:a", null).validate(true));
    assertFalse(headers(HttpMethod.CONNECT, null, "localhost:a").validate(true));
  }
}
