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

package io.vertx.core.http.headers;

import io.netty.handler.codec.DefaultHeaders;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.netty.incubator.codec.http3.Http3Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.headers.Http2HeadersAdaptor;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class HttpHeadersAdaptorsTestBase extends HeadersTestBase {

  protected DefaultHeaders<CharSequence, CharSequence, ?> headers;
  protected MultiMap map;

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
}
