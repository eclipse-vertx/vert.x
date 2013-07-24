/*
 * Copyright 2011-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.http.impl;

import io.netty.handler.codec.http.HttpHeaders;
import org.vertx.java.core.MultiMap;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class HttpHeadersAdapter implements MultiMap {
  private final HttpHeaders headers;

  public HttpHeadersAdapter(HttpHeaders headers) {
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
  public MultiMap add(String name, String value) {
    headers.add(name, value);
    return this;
  }

  @Override
  public MultiMap add(String name, Iterable<String> values) {
    headers.add(name, values);
    return this;
  }

  @Override
  public MultiMap add(MultiMap headers) {
    for (Map.Entry<String, String> entry: headers.entries()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap add(Map<String, String> map) {
    for (Map.Entry<String, String> entry: map.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap set(String name, String value) {
    headers.set(name, value);
    return this;
  }

  @Override
  public MultiMap set(String name, Iterable<String> values) {
    headers.set(name, values);
    return this;
  }

  @Override
  public MultiMap set(MultiMap httpHeaders) {
    clear();
    for (Map.Entry<String, String> entry: httpHeaders) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap remove(String name) {
    headers.remove(name);
    return this;
  }

  @Override
  public MultiMap clear() {
    headers.clear();
    return this;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return headers.iterator();
  }

  @Override
  public int size() {
    return names().size();
  }

  @Override
  public MultiMap set(Map<String, String> headers) {
    for (Map.Entry<String, String> entry: headers.entrySet()) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }
}
