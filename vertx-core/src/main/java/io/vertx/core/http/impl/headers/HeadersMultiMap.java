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

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.vertx.core.MultiMap;
import io.vertx.core.http.impl.HttpUtils;
import io.vertx.core.internal.http.HttpHeadersInternal;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * A case-insensitive {@link MultiMap} implementation that extends Netty {@link HttpHeaders}
 * for convenience.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class HeadersMultiMap extends HttpHeaders implements MultiMap {

  /**
   * Convert the {@code value} to a non null {@code CharSequence}
   * @param value the value
   * @return the char sequence
   */
  private static CharSequence toValidCharSequence(Object value) {
    if (value instanceof CharSequence) {
      return (CharSequence) value;
    } else {
      // Throws NPE
      return value.toString();
    }
  }

  static final BiConsumer<CharSequence, CharSequence> HTTP_VALIDATOR;

  static {
    if (!HttpHeadersInternal.DISABLE_HTTP_HEADERS_VALIDATION) {
      HTTP_VALIDATOR = HttpUtils::validateHeader;
    } else {
      HTTP_VALIDATOR = null;
    }
  }

  /**
   * @return a case-insensitive multimap suited for HTTP header validation
   */
  public static HeadersMultiMap httpHeaders() {
    return new HeadersMultiMap(HTTP_VALIDATOR);
  }

  /**
   * @return a all-purpose case-insensitive multimap that does not perform validation
   */
  public static HeadersMultiMap caseInsensitive() {
    return new HeadersMultiMap();
  }

  @Override
  public MultiMap setAll(MultiMap headers) {
    return set0(headers);
  }

  @Override
  public MultiMap setAll(Map<String, String> headers) {
    return set0(headers.entrySet());
  }

  @Override
  public int size() {
    return names().size();
  }

  private final BiConsumer<CharSequence, CharSequence> validator;
  private final HeadersMultiMap.MapEntry[] entries = new HeadersMultiMap.MapEntry[16];
  private final HeadersMultiMap.MapEntry head = new HeadersMultiMap.MapEntry();

  public HeadersMultiMap() {
    this(null);
  }

  public HeadersMultiMap(BiConsumer<CharSequence, CharSequence> validator) {
    this.validator = validator;
    head.before = head.after = head;
  }

  @Override
  public HeadersMultiMap add(CharSequence name, CharSequence value) {
    Objects.requireNonNull(value);
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    add0(h, i, name, value);
    return this;
  }

  @Override
  public HeadersMultiMap add(CharSequence name, Object value) {
    return add(name, toValidCharSequence(value));
  }

  @Override
  public HttpHeaders add(String name, Object value) {
    return add((CharSequence) name, toValidCharSequence(value));
  }

  @Override
  public HeadersMultiMap add(String name, String strVal) {
    return add((CharSequence) name, strVal);
  }

  @Override
  public HeadersMultiMap add(CharSequence name, Iterable values) {
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    for (Object vstr: values) {
      add0(h, i, name, toValidCharSequence(vstr));
    }
    return this;
  }

  @Override
  public HeadersMultiMap add(String name, Iterable values) {
    return add((CharSequence) name, values);
  }

  @Override
  public MultiMap addAll(MultiMap headers) {
    return addAll(headers.entries());
  }

  @Override
  public MultiMap addAll(Map<String, String> map) {
    return addAll(map.entrySet());
  }

  private MultiMap addAll(Iterable<Map.Entry<String, String>> headers) {
    for (Map.Entry<String, String> entry: headers) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }

  @Override
  public HeadersMultiMap remove(CharSequence name) {
    Objects.requireNonNull(name, "name");
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    remove0(h, i, name);
    return this;
  }

  @Override
  public HeadersMultiMap remove(final String name) {
    return remove((CharSequence) name);
  }

  @Override
  public HeadersMultiMap set(CharSequence name, CharSequence value) {
    return set0(name, value);
  }

  @Override
  public HeadersMultiMap set(String name, String value) {
    return set((CharSequence)name, value);
  }

  @Override
  public HeadersMultiMap set(String name, Object value) {
    return set((CharSequence)name, toValidCharSequence(value));
  }

  @Override
  public HeadersMultiMap set(CharSequence name, Object value) {
    return set(name, toValidCharSequence(value));
  }

  @Override
  public HeadersMultiMap set(CharSequence name, Iterable values) {
    Objects.requireNonNull(values, "values");

    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;

    remove0(h, i, name);
    for (Object v: values) {
      if (v == null) {
        break;
      }
      add0(h, i, name, toValidCharSequence(v));
    }

    return this;
  }

  @Override
  public HeadersMultiMap set(String name, Iterable values) {
    return set((CharSequence) name, values);
  }

  @Override
  public boolean containsValue(CharSequence name, CharSequence value, boolean ignoreCase) {
    return containsInternal(name, value, false, ignoreCase);
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
    return containsInternal(name, value, true, ignoreCase);
  }

  private boolean containsInternal(CharSequence name, CharSequence value, boolean equals, boolean ignoreCase) {
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    HeadersMultiMap.MapEntry e = entries[i];
    while (e != null) {
      CharSequence key = e.key;
      if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
        CharSequence other = e.getValue();
        if (equals) {
          if ((ignoreCase && AsciiString.contentEqualsIgnoreCase(value, other)) || (!ignoreCase && AsciiString.contentEquals(value, other))) {
            return true;
          }
        } else {
          int prev = 0;
          while (true) {
            final int idx = AsciiString.indexOf(other, ',', prev);
            int to;
            if (idx == -1) {
              to = other.length();
            } else {
              to = idx;
            }
            while (to > prev && other.charAt(to - 1) == ' ') {
              to--;
            }
            int from = prev;
            while (from < to && other.charAt(from) == ' ') {
              from++;
            }
            int len = to - from;
            if (len > 0 && AsciiString.regionMatches(other, ignoreCase, from, value, 0, len)) {
              return true;
            } else if (idx == -1) {
              break;
            }
            prev = idx + 1;
          }
        }
      }
      e = e.next;
    }
    return false;
  }

  @Override
  public boolean contains(String name, String value, boolean ignoreCase) {
    return contains((CharSequence) name, value, ignoreCase);
  }

  @Override
  public boolean contains(CharSequence name) {
    return get0(name) != null;
  }

  @Override
  public boolean contains(String name) {
    return contains((CharSequence) name);
  }

  @Override
  public String get(CharSequence name) {
    Objects.requireNonNull(name, "name");
    CharSequence ret = get0(name);
    return ret != null ? ret.toString() : null;
  }

  @Override
  public String get(String name) {
    return get((CharSequence) name);
  }

  @Override
  public List<String> getAll(CharSequence name) {
    Objects.requireNonNull(name, "name");
    LinkedList<String> values = null;
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    HeadersMultiMap.MapEntry e = entries[i];
    while (e != null) {
      CharSequence key = e.key;
      if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
        if (values == null) {
          values = new LinkedList<>();
        }
        values.addFirst(e.getValue().toString());
      }
      e = e.next;
    }
    return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
  }

  @Override
  public List<String> getAll(String name) {
    return getAll((CharSequence) name);
  }

  @Override
  public void forEach(Consumer<? super Map.Entry<String, String>> action) {
    HeadersMultiMap.MapEntry e = head.after;
    while (e != head) {
      action.accept(e.stringEntry());
      e = e.after;
    }
  }

  @Override
  public void forEach(BiConsumer<String, String> action) {
    HeadersMultiMap.MapEntry e = head.after;
    while (e != head) {
      action.accept(e.getKey().toString(), e.getValue().toString());
      e = e.after;
    }
  }

  @Override
  public List<Map.Entry<String, String>> entries() {
    return MultiMap.super.entries();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return new Iterator<Map.Entry<String, String>>() {
      MapEntry curr = head;
      @Override
      public boolean hasNext() {
        return curr.after != head;
      }
      @Override
      public Map.Entry<String, String> next() {
        MapEntry next = curr.after;
        if (next == head){
          throw new NoSuchElementException();
        }
        curr = next;
        return new Map.Entry<String, String>() {
          @Override
          public String getKey() {
            return next.key.toString();
          }
          @Override
          public String getValue() {
            return next.value.toString();
          }
          @Override
          public String setValue(String value) {
            return next.setValue(value).toString();
          }
          @Override
          public String toString() {
            return getKey() + "=" + getValue();
          }
        };
      }
    };
  }

  @Override
  public boolean isEmpty() {
    return head == head.after;
  }

  @Override
  public Set<String> names() {
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    HeadersMultiMap.MapEntry e = head.after;
    while (e != head) {
      names.add(e.getKey().toString());
      e = e.after;
    }
    return names;
  }

  @Override
  public HeadersMultiMap clear() {
    Arrays.fill(entries, null);
    head.before = head.after = head;
    return this;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, String> entry: this) {
      sb.append(entry).append('\n');
    }
    return sb.toString();
  }

  @Override
  public Integer getInt(CharSequence name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public int getInt(CharSequence name, int defaultValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Short getShort(CharSequence name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public short getShort(CharSequence name, short defaultValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Long getTimeMillis(CharSequence name) {
    throw new UnsupportedOperationException();
  }

  @Override
  public long getTimeMillis(CharSequence name, long defaultValue) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Iterator<Map.Entry<CharSequence, CharSequence>> iteratorCharSequence() {
    return new Iterator<Map.Entry<CharSequence, CharSequence>>() {
      HeadersMultiMap.MapEntry current = head.after;
      @Override
      public boolean hasNext() {
        return current != head;
      }
      @Override
      public Map.Entry<CharSequence, CharSequence> next() {
        Map.Entry<CharSequence, CharSequence> next = current;
        current = current.after;
        return next;
      }
    };
  }

  @Override
  public HttpHeaders addInt(CharSequence name, int value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpHeaders addShort(CharSequence name, short value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpHeaders setInt(CharSequence name, int value) {
    return set(name, Integer.toString(value));
  }

  @Override
  public HttpHeaders setShort(CharSequence name, short value) {
    throw new UnsupportedOperationException();
  }

  public void encode(ByteBuf buf) {
    HeadersMultiMap.MapEntry current = head.after;
    while (current != head) {
      encoderHeader(current.key, current.value, buf);
      current = current.after;
    }
  }

  private static final int COLON_AND_SPACE_SHORT = (COLON << 8) | SP;
  static final int CRLF_SHORT = (CR << 8) | LF;

  static void encoderHeader(CharSequence name, CharSequence value, ByteBuf buf) {
    final int nameLen = name.length();
    final int valueLen = value.length();
    final int entryLen = nameLen + valueLen + 4;
    buf.ensureWritable(entryLen);
    int offset = buf.writerIndex();
    writeAscii(buf, offset, name);
    offset += nameLen;
    ByteBufUtil.setShortBE(buf, offset, COLON_AND_SPACE_SHORT);
    offset += 2;
    writeAscii(buf, offset, value);
    offset += valueLen;
    ByteBufUtil.setShortBE(buf, offset, CRLF_SHORT);
    offset += 2;
    buf.writerIndex(offset);
  }

  private static void writeAscii(ByteBuf buf, int offset, CharSequence value) {
    if (value instanceof AsciiString) {
      ByteBufUtil.copy((AsciiString) value, 0, buf, offset, value.length());
    } else {
      buf.setCharSequence(offset, value, CharsetUtil.US_ASCII);
    }
  }

  private final class MapEntry implements Map.Entry<CharSequence, CharSequence> {
    final int hash;
    final CharSequence key;
    CharSequence value;
    HeadersMultiMap.MapEntry next;
    HeadersMultiMap.MapEntry before, after;

    MapEntry() {
      this.hash = -1;
      this.key = null;
      this.value = null;
    }

    MapEntry(int hash, CharSequence key, CharSequence value) {
      this.hash = hash;
      this.key = key;
      this.value = value;
    }

    void remove() {
      before.after = after;
      after.before = before;
      after = null;
      before = null;
    }

    void addBefore(HeadersMultiMap.MapEntry e) {
      after  = e;
      before = e.before;
      before.after = this;
      after.before = this;
    }

    @Override
    public CharSequence getKey() {
      return key;
    }

    @Override
    public CharSequence getValue() {
      return value;
    }

    @Override
    public CharSequence setValue(CharSequence value) {
      Objects.requireNonNull(value, "value");
      if (validator != null) {
        validator.accept("", value);
      }
      CharSequence oldValue = this.value;
      this.value = value;
      return oldValue;
    }

    @Override
    public String toString() {
      return getKey() + "=" + getValue();
    }

    @SuppressWarnings({"rawtypes","unchecked"})
    private Map.Entry<String, String> stringEntry() {
      if (key instanceof String && value instanceof String) {
        return (Map.Entry) this;
      } else {
        return new AbstractMap.SimpleEntry<>(key.toString(), value.toString());
      }
    }
  }

  private void remove0(int h, int i, CharSequence name) {
    HeadersMultiMap.MapEntry e = entries[i];
    MapEntry prev = null;
    while (e != null) {
      MapEntry next = e.next;
      CharSequence key = e.key;
      if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
        if (prev == null) {
          entries[i] = next;
        } else {
          prev.next = next;
        }
        e.remove();
      } else {
        prev = e;
      }
      e = next;
    }
  }

  private void add0(int h, int i, final CharSequence name, final CharSequence value) {
    if (validator != null) {
      validator.accept(name, value);
    }
    // Update the hash table.
    HeadersMultiMap.MapEntry e = entries[i];
    HeadersMultiMap.MapEntry newEntry;
    entries[i] = newEntry = new HeadersMultiMap.MapEntry(h, name, value);
    newEntry.next = e;

    // Update the linked list.
    newEntry.addBefore(head);
  }

  private HeadersMultiMap set0(final CharSequence name, final CharSequence strVal) {
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    remove0(h, i, name);
    if (strVal != null) {
      add0(h, i, name, strVal);
    }
    return this;
  }

  private CharSequence get0(CharSequence name) {
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    HeadersMultiMap.MapEntry e = entries[i];
    CharSequence value = null;
    while (e != null) {
      CharSequence key = e.key;
      if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
        value = e.getValue();
      }
      e = e.next;
    }
    return value;
  }

  private MultiMap set0(Iterable<Map.Entry<String, String>> map) {
    clear();
    for (Map.Entry<String, String> entry: map) {
      add(entry.getKey(), entry.getValue());
    }
    return this;
  }
}
