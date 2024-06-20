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

package io.vertx.core;

import io.vertx.codegen.annotations.Nullable;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class MultiMapTest {
  @Test
  public void getStringShouldReturnDefaultValueWhenMultimapContainsNoMappingsForTheKey() {
    MultiMap emptyMultimap = createMultimapWithSingleKey();

    String actualValue = emptyMultimap.get("missing key", "the default value");

    assertEquals("the default value", actualValue);
  }

  @Test
  public void getStringShouldReturnPresentValueWhenMultimapContainsMappingForTheKey() {
    MultiMap emptyMultimap = createMultimapWithSingleKey();

    String actualValue = emptyMultimap.get("present key", "the default value");

    assertEquals("present value", actualValue);
  }

  @Test
  public void getSupplierShouldUseDefaultValueSupplierWhenMultimapContainsNoMappingsForTheKey() {
    MultiMap emptyMultimap = createMultimapWithSingleKey();

    String actualValue = emptyMultimap.get("missing key", () -> "the default value");

    assertEquals("the default value", actualValue);
  }

  @Test
  public void getSupplierShouldReturnPresentValueWhenMultimapContainsMappingForTheKey() {
    MultiMap emptyMultimap = createMultimapWithSingleKey();

    String actualValue = emptyMultimap.get("present key", () -> "the default value");

    assertEquals("present value", actualValue);
  }

  @Test
  public void getUnaryOperatorShouldUseDefaultValueFunctionWhenMultimapContainsNoMappingsForTheKey() {
    MultiMap emptyMultimap = createMultimapWithSingleKey();

    String actualValue = emptyMultimap.get("missing key", key -> "the default value of the " + key);

    assertEquals("the default value of the missing key", actualValue);
  }

  @Test
  public void getUnaryOperatorShouldReturnPresentValueWhenMultimapContainsMappingForTheKey() {
    MultiMap emptyMultimap = createMultimapWithSingleKey();

    String actualValue = emptyMultimap.get("present key", key -> "the default value of the " + key);

    assertEquals("present value", actualValue);
  }

  private MultiMap createMultimapWithSingleKey() {
    return new MultiMap() {
      @Override
      public String get(CharSequence name) {
        return null;
      }

      @Override
      public @Nullable String get(String name) {
        if ("present key".equals(name)) {
          return "present value";
        } else {
          return null;
        }
      }

      @Override
      public List<String> getAll(String name) {
        return null;
      }

      @Override
      public List<String> getAll(CharSequence name) {
        return null;
      }

      @Override
      public boolean contains(String name) {
        return false;
      }

      @Override
      public boolean contains(CharSequence name) {
        return false;
      }

      @Override
      public boolean isEmpty() {
        return false;
      }

      @Override
      public Set<String> names() {
        return null;
      }

      @Override
      public MultiMap add(String name, String value) {
        return null;
      }

      @Override
      public MultiMap add(CharSequence name, CharSequence value) {
        return null;
      }

      @Override
      public MultiMap add(String name, Iterable<String> values) {
        return null;
      }

      @Override
      public MultiMap add(CharSequence name, Iterable<CharSequence> values) {
        return null;
      }

      @Override
      public MultiMap addAll(MultiMap map) {
        return null;
      }

      @Override
      public MultiMap addAll(Map<String, String> headers) {
        return null;
      }

      @Override
      public MultiMap set(String name, String value) {
        return null;
      }

      @Override
      public MultiMap set(CharSequence name, CharSequence value) {
        return null;
      }

      @Override
      public MultiMap set(String name, Iterable<String> values) {
        return null;
      }

      @Override
      public MultiMap set(CharSequence name, Iterable<CharSequence> values) {
        return null;
      }

      @Override
      public MultiMap setAll(MultiMap map) {
        return null;
      }

      @Override
      public MultiMap setAll(Map<String, String> headers) {
        return null;
      }

      @Override
      public MultiMap remove(String name) {
        return null;
      }

      @Override
      public MultiMap remove(CharSequence name) {
        return null;
      }

      @Override
      public MultiMap clear() {
        return null;
      }

      @Override
      public int size() {
        return 0;
      }

      @Override
      public Iterator<Map.Entry<String, String>> iterator() {
        return null;
      }
    };
  }
}
