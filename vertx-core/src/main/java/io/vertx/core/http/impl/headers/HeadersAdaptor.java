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

package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.http.HttpHeaders;
import io.vertx.core.MultiMap;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HeadersAdaptor implements MultiMap, io.vertx.core.http.HttpHeaders {

  private final boolean mutable;
  private final HttpHeaders headers;

  public HeadersAdaptor(HttpHeaders headers) {
    this(true, headers);
  }

  private HeadersAdaptor(boolean mutable, HttpHeaders headers) {
    this.mutable = mutable;
    this.headers = headers;
  }

  @Override
  public String get(String name) {
    return headers.get(name);
  }

  @Override
  public List<String> getAll(String name) {
    return headers.getAll(name);
  }

  @Override
  public List<Map.Entry<String, String>> entries() {
    return headers.entries();
  }

  @Override
  public boolean contains(String name) {
    return headers.contains(name);
  }

  @Override
  public boolean isEmpty() {
    return headers.isEmpty();
  }

  @Override
  public Set<String> names() {
    return headers.names();
  }

  @Override
  public HeadersAdaptor add(String name, String value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.add(name, value);
    return this;
  }

  @Override
  public HeadersAdaptor add(String name, Iterable<String> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.add(name, values);
    return this;
  }

  @Override
  public HeadersAdaptor addAll(MultiMap headers) {
    for (Map.Entry<String, String> entry: headers.entries()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public HeadersAdaptor addAll(Map<String, String> map) {
    for (Map.Entry<String, String> entry: map.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public HeadersAdaptor set(String name, String value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (value != null) {
      headers.set(name, value);
    } else {
      headers.remove(name);
    }
    return this;
  }

  @Override
  public HeadersAdaptor set(String name, Iterable<String> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.set(name, values);
    return this;
  }

  @Override
  public HeadersAdaptor setAll(MultiMap httpHeaders) {
    clear();
    for (Map.Entry<String, String> entry: httpHeaders) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public HeadersAdaptor remove(String name) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.remove(name);
    return this;
  }

  @Override
  public HeadersAdaptor clear() {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.clear();
    return this;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return headers.iteratorAsString();
  }

  @Override
  public int size() {
    return names().size();
  }

  @Override
  public HeadersAdaptor setAll(Map<String, String> headers) {
    clear();
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public String get(CharSequence name) {
    return headers.get(name);
  }

  @Override
  public List<String> getAll(CharSequence name) {
    return headers.getAll(name);
  }

  @Override
  public boolean contains(CharSequence name) {
    return headers.contains(name);
  }

  @Override
  public boolean contains(String name, String value, boolean caseInsensitive) {
    return headers.contains(name, value, caseInsensitive);
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value, boolean caseInsensitive) {
    return headers.contains(name, value, caseInsensitive);
  }

  @Override
  public HeadersAdaptor add(CharSequence name, CharSequence value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.add(name, value);
    return this;
  }

  @Override
  public HeadersAdaptor add(CharSequence name, Iterable<CharSequence> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.add(name, values);
    return this;
  }

  @Override
  public HeadersAdaptor set(CharSequence name, CharSequence value) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    if (value != null) {
      headers.set(name, value);
    } else {
      headers.remove(name);
    }
    return this;
  }

  @Override
  public HeadersAdaptor set(CharSequence name, Iterable<CharSequence> values) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.set(name, values);
    return this;
  }

  @Override
  public HeadersAdaptor remove(CharSequence name) {
    if (!mutable) {
      throw new IllegalStateException("Read only");
    }
    headers.remove(name);
    return this;
  }

  @Override
  public boolean isMutable() {
    return mutable;
  }

  @Override
  public io.vertx.core.http.HttpHeaders copy(boolean mutable) {
    return new HeadersAdaptor(mutable, headers.copy());
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry: this) {
      sb.append(entry).append('\n');
    }
    return sb.toString();
  }
}
