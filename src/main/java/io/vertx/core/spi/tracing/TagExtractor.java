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
package io.vertx.core.spi.tracing;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * This provides an utility for extracting request/response tags, potentially allocation-free.
 *
 * <p> Tag should follow the <a href="https://github.com/opentracing/specification/blob/master/semantic_conventions.md">OpenTracing</a> tag
 * names. This might change in the future if a new spec with a superset (semantically wise) of this list.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface TagExtractor<T> {

  @SuppressWarnings("unchecked")
  static <T> TagExtractor<T> empty() {
    return Extractors.EMPTY;
  }

  /**
   * Returns the number of tags {@code obj} exposes.
   *
   * @param obj the object to evaluate
   * @return the number of tags
   */
  default int len(T obj) {
    return 0;
  }

  /**
   * Returns the name of the tag extracted from {@code obj}at the specified {@code index}.
   *
   * @param obj the object to extract the tag name from
   * @param index the index of the tag
   * @return the tag name
   */
  default String name(T obj, int index) {
    throw new IndexOutOfBoundsException("Invalid tag index " + index);
  }

  /**
   * Returns the value of the tag extracted from {@code obj} at the specified {@code index}.
   *
   * @param obj the object to extract the tag name from
   * @param index the index of the tag
   * @return the tag value
   */
  default String value(T obj, int index) {
    throw new IndexOutOfBoundsException("Invalid tag index " + index);
  }

  /**
   * Extract all the tags from {@code obj} into a {@code Map<String, String>}.
   *
   * @param obj the object to extract the tags from
   * @return the map of all tags extracted from the object
   */
  default Map<String, String> extract(T obj) {
    Map<String, String> tags = new HashMap<>();
    extractTo(obj, tags);
    return tags;
  }

  /**
   * Extract all the tags from {@code obj} into the {@code tags} map.
   *
   * @param obj the object to extract the tags from
   * @param tags the map populated with the tags extracted from the object
   */
  default void extractTo(T obj, Map<String, String> tags) {
    int len = len(obj);
    for (int idx = 0;idx < len;idx++) {
      tags.put(name(obj, idx), value(obj, idx));
    }
  }

  /**
   * Extract all the tags from {@code obj} into a tag {@code consumer}.
   *
   * @param obj the object to extract the tags from
   * @param consumer the consumer populated with the tags extracted from the object
   */
  default void extractTo(T obj, BiConsumer<String, String> consumer) {
    int len = len(obj);
    for (int idx = 0;idx < len;idx++) {
      consumer.accept(name(obj, idx), value(obj, idx));
    }
  }
}
