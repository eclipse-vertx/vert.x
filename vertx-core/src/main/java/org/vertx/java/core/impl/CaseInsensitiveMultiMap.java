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

package org.vertx.java.core.impl;


import org.vertx.java.core.MultiMap;

import java.util.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public final class CaseInsensitiveMultiMap implements MultiMap {
  private static final int BUCKET_SIZE = 17;

  private static int hash(String name) {
    int h = 0;
    for (int i = name.length() - 1; i >= 0; i --) {
      char c = name.charAt(i);
      if (c >= 'A' && c <= 'Z') {
        c += 32;
      }
      h = 31 * h + c;
    }

    if (h > 0) {
      return h;
    } else if (h == Integer.MIN_VALUE) {
      return Integer.MAX_VALUE;
    } else {
      return -h;
    }
  }

  private MultiMap set0(Iterable<Map.Entry<String, String>> map) {
    clear();
    for (Map.Entry<String, String> entry: map) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public MultiMap set(MultiMap headers) {
    return set0(headers);
  }

  @Override
  public MultiMap set(Map<String, String> headers) {
    return set0(headers.entrySet());
  }

  @Override
  public int size() {
    return names().size();
  }

  private static boolean eq(String name1, String name2) {
    int nameLen = name1.length();
    if (nameLen != name2.length()) {
      return false;
    }

    for (int i = nameLen - 1; i >= 0; i --) {
      char c1 = name1.charAt(i);
      char c2 = name2.charAt(i);
      if (c1 != c2) {
        if (c1 >= 'A' && c1 <= 'Z') {
          c1 += 32;
        }
        if (c2 >= 'A' && c2 <= 'Z') {
          c2 += 32;
        }
        if (c1 != c2) {
          return false;
        }
      }
    }
    return true;
  }

  private static int index(int hash) {
    return hash % BUCKET_SIZE;
  }

  private final MapEntry[] entries = new MapEntry[BUCKET_SIZE];
  private final MapEntry head = new MapEntry(-1, null, null);

  public CaseInsensitiveMultiMap() {
    head.before = head.after = head;
  }

  @Override
  public MultiMap add(final String name, final String strVal) {
    int h = hash(name);
    int i = index(h);
    add0(h, i, name, strVal);
    return this;
  }

  @Override
  public MultiMap add(String name, Iterable<String> values) {
    int h = hash(name);
    int i = index(h);
    for (String vstr: values) {
      add0(h, i, name, vstr);
    }
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

  private void add0(int h, int i, final String name, final String value) {
    // Update the hash table.
    MapEntry e = entries[i];
    MapEntry newEntry;
    entries[i] = newEntry = new MapEntry(h, name, value);
    newEntry.next = e;

    // Update the linked list.
    newEntry.addBefore(head);
  }

  @Override
  public MultiMap remove(final String name) {
    if (name == null) {
      throw new NullPointerException("name");
    }
    int h = hash(name);
    int i = index(h);
    remove0(h, i, name);
    return this;
  }

  private void remove0(int h, int i, String name) {
    MapEntry e = entries[i];
    if (e == null) {
      return;
    }

    for (;;) {
      if (e.hash == h && eq(name, e.key)) {
        e.remove();
        MapEntry next = e.next;
        if (next != null) {
          entries[i] = next;
          e = next;
        } else {
          entries[i] = null;
          return;
        }
      } else {
        break;
      }
    }

    for (;;) {
      MapEntry next = e.next;
      if (next == null) {
        break;
      }
      if (next.hash == h && eq(name, next.key)) {
        e.next = next.next;
        next.remove();
      } else {
        e = next;
      }
    }
  }

  @Override
  public MultiMap set(final String name, final String strVal) {
    int h = hash(name);
    int i = index(h);
    remove0(h, i, name);
    add0(h, i, name, strVal);
    return this;
  }

  @Override
  public MultiMap set(final String name, final Iterable<String> values) {
    if (values == null) {
      throw new NullPointerException("values");
    }

    int h = hash(name);
    int i = index(h);

    remove0(h, i, name);
    for (String v: values) {
      if (v == null) {
        break;
      }
      add0(h, i, name, v);
    }

    return this;
  }

  @Override
  public MultiMap clear() {
    for (int i = 0; i < entries.length; i ++) {
      entries[i] = null;
    }
    head.before = head.after = head;
    return this;
  }

  @Override
  public String get(final String name) {
    if (name == null) {
      throw new NullPointerException("name");
    }

    int h = hash(name);
    int i = index(h);
    MapEntry e = entries[i];
    while (e != null) {
      if (e.hash == h && eq(name, e.key)) {
        return e.value;
      }

      e = e.next;
    }
    return null;
  }

  @Override
  public List<String> getAll(final String name) {
    if (name == null) {
      throw new NullPointerException("name");
    }

    LinkedList<String> values = new LinkedList<>();

    int h = hash(name);
    int i = index(h);
    MapEntry e = entries[i];
    while (e != null) {
      if (e.hash == h && eq(name, e.key)) {
        values.addFirst(e.value);
      }
      e = e.next;
    }
    return values;
  }

  @Override
  public List<Map.Entry<String, String>> entries() {
    List<Map.Entry<String, String>> all =
            new LinkedList<>();

    MapEntry e = head.after;
    while (e != head) {
      all.add(e);
      e = e.after;
    }
    return all;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return entries().iterator();
  }

  @Override
  public boolean contains(String name) {
    return get(name) != null;
  }

  @Override
  public boolean isEmpty() {
    return head == head.after;
  }

  @Override
  public Set<String> names() {

    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

    MapEntry e = head.after;
    while (e != head) {
      names.add(e.key);
      e = e.after;
    }
    return names;
  }

  private static final class MapEntry implements Map.Entry<String, String> {
    final int hash;
    final String key;
    String value;
    MapEntry next;
    MapEntry before, after;

    MapEntry(int hash, String key, String value) {
      this.hash = hash;
      this.key = key;
      this.value = value;
    }

    void remove() {
      before.after = after;
      after.before = before;
    }

    void addBefore(MapEntry e) {
      after  = e;
      before = e.before;
      before.after = this;
      after.before = this;
    }

    @Override
    public String getKey() {
      return key;
    }

    @Override
    public String getValue() {
      return value;
    }

    @Override
    public String setValue(String value) {
      if (value == null) {
        throw new NullPointerException("value");
      }
      String oldValue = this.value;
      this.value = value;
      return oldValue;
    }

    @Override
    public String toString() {
      return key + '=' + value;
    }
  }
}
