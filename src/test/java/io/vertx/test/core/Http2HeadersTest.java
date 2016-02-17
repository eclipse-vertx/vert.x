/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.Http2HeadersAdaptor;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class Http2HeadersTest {

  DefaultHttp2Headers headers;
  MultiMap map;

  @Before
  public void setUp() {
    headers = new DefaultHttp2Headers();
    map = new Http2HeadersAdaptor(headers);
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

  private void assertHeaderNames(String... expected) {
    assertEquals(new HashSet<>(Arrays.asList(expected)), headers.names().stream().map(CharSequence::toString).collect(Collectors.toSet()));
  }
}
