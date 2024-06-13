/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http.headers;

import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http2.DefaultHttp2Headers;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.HeadersMultiMap;
import io.vertx.impl.core.http.headers.Http2HeadersAdaptor;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.junit.Assert.assertEquals;

public class MultiMapInteropTest {

  @Test
  public void testSetAllOnExistingMapUsingMultiMapHttp1() {
    testSetAllOnExistingMapUsingMultiMapHttp1(new HeadersMultiMap());
    testSetAllOnExistingMapUsingMultiMapHttp1(HeadersMultiMap.httpHeaders());
    testSetAllOnExistingMapUsingMultiMapHttp1(new io.vertx.core.http.impl.headers.HeadersAdaptor(new DefaultHttpHeaders()));
    testSetAllOnExistingMapUsingMultiMapHttp1(new Http2HeadersAdaptor(new DefaultHttp2Headers()));
  }

  private void testSetAllOnExistingMapUsingMultiMapHttp1(MultiMap setAllMap) {
    MultiMap mainMap = new io.vertx.core.http.impl.headers.HeadersAdaptor(HeadersMultiMap.httpHeaders());
    mainMap.add("originalKey", "originalValue");

    setAllMap.add("originalKey", "newValue");
    setAllMap.add("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testSetAllOnExistingMapUsingHashMapHttp1() {
    MultiMap mainMap = new io.vertx.core.http.impl.headers.HeadersAdaptor(HeadersMultiMap.httpHeaders());
    mainMap.add("originalKey", "originalValue");

    Map<String,String> setAllMap = new HashMap<>();

    setAllMap.put("originalKey", "newValue");
    setAllMap.put("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testSetAllOnExistingMapUsingMultiMapHttp2() {
    testSetAllOnExistingMapUsingMultiMapHttp2(new HeadersMultiMap());
    testSetAllOnExistingMapUsingMultiMapHttp2(HeadersMultiMap.httpHeaders());
    testSetAllOnExistingMapUsingMultiMapHttp2(new io.vertx.core.http.impl.headers.HeadersAdaptor(new DefaultHttpHeaders()));
    testSetAllOnExistingMapUsingMultiMapHttp2(new Http2HeadersAdaptor(new DefaultHttp2Headers()));
  }

  private void testSetAllOnExistingMapUsingMultiMapHttp2(MultiMap setAllMap) {
    MultiMap mainMap = new Http2HeadersAdaptor(new DefaultHttp2Headers());
    mainMap.add("originalKey", "originalValue");

    setAllMap.add("originalKey", "newValue");
    setAllMap.add("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

  @Test
  public void testSetAllOnExistingMapUsingHashMapHttp2() {
    MultiMap mainMap = new Http2HeadersAdaptor(new DefaultHttp2Headers());
    mainMap.add("originalKey", "originalValue");

    Map<String,String> setAllMap = new HashMap<>();

    setAllMap.put("originalKey", "newValue");
    setAllMap.put("anotherKey", "anotherValue");

    MultiMap result = mainMap.setAll(setAllMap);
    assertNotNull(result);
    assertFalse(result.isEmpty());
    assertEquals(2, result.size());
    assertEquals("newValue",result.get("originalKey"));
    assertEquals("anotherValue",result.get("anotherKey"));
  }

}
