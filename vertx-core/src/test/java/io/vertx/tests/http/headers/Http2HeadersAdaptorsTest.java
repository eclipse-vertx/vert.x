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

import io.netty.handler.codec.Headers;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.headers.HttpHeaders;
import io.vertx.core.http.impl.headers.HttpRequestHeaders;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2HeadersAdaptorsTest extends HeadersTest {

  @Override
  protected HttpHeaders newMultiMap() {
    return new HttpHeaders(new DefaultHttp2Headers());
  }

  @Test
  public void testGetConvertUpperCase() {
    HttpHeaders headers = newMultiMap();
    headers.set("foo", "foo_value");
    assertEquals("foo_value", headers.get("Foo"));
    assertEquals("foo_value", headers.get((CharSequence) "Foo"));
  }

  @Test
  public void testGetAllConvertUpperCase() {
    HttpHeaders headers = newMultiMap();
    headers.set("foo", "foo_value");
    assertEquals(Collections.singletonList("foo_value"), headers.getAll("Foo"));
    assertEquals(Collections.singletonList("foo_value"), headers.getAll((CharSequence) "Foo"));
  }

  @Test
  public void testContainsConvertUpperCase() {
    HttpHeaders headers = newMultiMap();
    headers.set("foo", "foo_value");
    assertTrue(headers.contains("Foo"));
    assertTrue(headers.contains((CharSequence) "Foo"));
  }

  @Test
  public void testSetConvertUpperCase() {
    HttpHeaders headers = newMultiMap();
    headers.set("Foo", "foo_value");
    headers.set((CharSequence) "Bar", "bar_value");
    headers.set("Juu", (Iterable<String>)Collections.singletonList("juu_value"));
    headers.set("Daa", Collections.singletonList((CharSequence)"daa_value"));
    assertHeaderNames(headers, "foo","bar", "juu", "daa");
  }

  @Test
  public void testAddConvertUpperCase() {
    HttpHeaders headers = newMultiMap();
    headers.add("Foo", "foo_value");
    headers.add((CharSequence) "Bar", "bar_value");
    headers.add("Juu", (Iterable<String>)Collections.singletonList("juu_value"));
    headers.add("Daa", Collections.singletonList((CharSequence)"daa_value"));
    assertHeaderNames(headers, "foo","bar", "juu", "daa");
  }

  @Test
  public void testRemoveConvertUpperCase() {
    HttpHeaders headers = newMultiMap();
    headers.set("foo", "foo_value");
    headers.remove("Foo");
    headers.set("bar", "bar_value");
    headers.remove((CharSequence) "Bar");
    assertHeaderNames(headers);
  }

  @Ignore
  @Test
  public void testEntries() {
    HttpHeaders headers = newMultiMap();
    headers.set("foo", Arrays.<String>asList("foo_value_1", "foo_value_2"));
    List<Map.Entry<String, String>> entries = headers.entries();
    assertEquals(1, entries.size());
    assertEquals("foo", entries.get(0).getKey());
    assertEquals("foo_value_1", entries.get(0).getValue());
    headers.set("bar", "bar_value");
    Map<String, String> collected = headers.entries().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    assertEquals("foo_value_1", collected.get("foo"));
    assertEquals("bar_value", collected.get("bar"));
  }

  private void assertHeaderNames(HttpHeaders headers,  String... expected) {
    assertEquals(new HashSet<>(Arrays.asList(expected)), headers.unwrap().names().stream().map(CharSequence::toString).collect(Collectors.toSet()));
  }

  private HttpHeaders headers(HttpMethod method, String authority, String host) {
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
    return new HttpRequestHeaders(s);
  }

  @Test
  public void testAuthorityValidation() {
    assertTrue(headers(HttpMethod.GET, null, null).validate());
    assertTrue(headers(HttpMethod.GET, "localhost:8080", null).validate());
    assertTrue(headers(HttpMethod.GET, null, "localhost:8080").validate());
    assertTrue(headers(HttpMethod.GET, "localhost:8080", "localhost:8080").validate());
    assertFalse(headers(HttpMethod.GET, "localhost:8080", "localhost:8081").validate());
    assertFalse(headers(HttpMethod.GET, "localhost:a", null).validate());
    assertFalse(headers(HttpMethod.GET, null, "localhost:a").validate());
    assertFalse(headers(HttpMethod.CONNECT, null, null).validate());
    assertTrue(headers(HttpMethod.CONNECT, "localhost:8080", null).validate());
    assertTrue(headers(HttpMethod.CONNECT, null, "localhost:8080").validate());
    assertTrue(headers(HttpMethod.CONNECT, "localhost:8080", "localhost:8080").validate());
    assertFalse(headers(HttpMethod.CONNECT, "localhost:8080", "localhost:8081").validate());
    assertFalse(headers(HttpMethod.CONNECT, "localhost:a", null).validate());
    assertFalse(headers(HttpMethod.CONNECT, null, "localhost:a").validate());
  }

  @Test
  public void testFilterPseudoHeaders() {
    HttpHeaders headers = headers(HttpMethod.PUT, "localhost:8080", "another:4443");
    List<String> names = new ArrayList<>();
    headers.forEach(entry -> {
      names.add(entry.getKey());
    });
    assertEquals(List.of("host"), names);
    assertEquals(1, headers.size());
    HttpHeaders other = newMultiMap();
    other.setAll(headers);
    assertEquals(1, other.unwrap().size());
  }
}
