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

import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.netty.handler.codec.http.HttpConstants.*;

/**
 * A case-insensitive {@link io.vertx.core.http.HttpHeaders} implementation that extends Netty {@link HttpHeaders}.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public final class HeadersMultiMap extends HttpHeaders implements MultiMap {

  private static final int COLON_AND_SPACE_SHORT = (COLON << 8) | SP;
  private static final int CRLF_SHORT = (CR << 8) | LF;
  private static final BiConsumer<CharSequence, CharSequence> HTTP_VALIDATOR;

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
    return new HeadersMultiMap(false, HTTP_VALIDATOR);
  }

  /**
   * @return a case-insensitive multimap suited for HTTP header validation
   */
  public static HeadersMultiMap httpHeaders(BiConsumer<CharSequence, CharSequence> validator) {
    return new HeadersMultiMap(false, validator);
  }

  /**
   * @return a all-purpose case-insensitive multimap that does not perform validation
   */
  public static HeadersMultiMap caseInsensitive() {
    return new HeadersMultiMap(false, (BiConsumer<CharSequence, CharSequence>) null);
  }

  private final BiConsumer<CharSequence, CharSequence> validator;
  private final boolean readOnly;
  private HeadersMultiMap ref;
  private HeadersMultiMap.MapEntry[] entries;
  private HeadersMultiMap.MapEntry head;
  private HeadersMultiMap.MapEntry tail;
  private int modCount = 0;
  private Reference<byte[]> renderedBytesRef;

  private HeadersMultiMap(boolean readOnly, BiConsumer<CharSequence, CharSequence> validator) {
    this.head = null;
    this.entries = null;
    this.readOnly = readOnly;
    this.validator = validator;
    this.ref = null;
  }

  private HeadersMultiMap(boolean readOnly, HeadersMultiMap that) {
    this.head = null;
    this.entries = null;
    this.validator = that.validator;

    setAll((MultiMap) that);

    this.readOnly = readOnly;
    this.ref = null;
  }

  private HeadersMultiMap(boolean readOnly, MapEntry[] entries, MapEntry head, BiConsumer<CharSequence, CharSequence> validator, HeadersMultiMap ref) {
    this.readOnly = readOnly;
    this.head = head;
    this.entries = entries;
    this.validator = validator;
    this.ref = ref;
  }

  @Override
  public HeadersMultiMap setAll(MultiMap multimap) {
    if (multimap instanceof HeadersMultiMap) {
      HeadersMultiMap headers = (HeadersMultiMap) multimap;
      if (headers.readOnly) {
        ref = headers;
        head = headers.head;
        tail = headers.tail;
        entries = headers.entries;
      } else {
        clear0();
        addAll(headers);
      }
    } else {
      setAll((Iterable<Map.Entry<String, String>>) multimap);
    }
    modCount++;
    return this;
  }

  @Override
  public HeadersMultiMap setAll(Map<String, String> headers) {
    setAll(headers.entrySet());
    modCount++;
    return this;
  }

  @Override
  public int size() {
    return names().size();
  }

  @Override
  public HeadersMultiMap add(CharSequence name, CharSequence value) {
    Objects.requireNonNull(value);
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    add0(h, i, name, value);
    modCount++;
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
    modCount++;
    return this;
  }

  @Override
  public HeadersMultiMap add(String name, Iterable values) {
    return add((CharSequence) name, values);
  }

  @Override
  public HeadersMultiMap addAll(MultiMap headers) {
    return addAll(headers.entries());
  }

  @Override
  public HeadersMultiMap addAll(Map<String, String> map) {
    return addAll(map.entrySet());
  }

  public HeadersMultiMap addAll(Iterable<Map.Entry<String, String>> headers) {
    for (Map.Entry<String, String> entry: headers) {
      add(entry.getKey(), entry.getValue());
    }
    modCount++;
    return this;
  }

  @Override
  public HeadersMultiMap remove(CharSequence name) {
    Objects.requireNonNull(name, "name");
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    remove0(h, i, name);
    modCount++;
    return this;
  }

  @Override
  public HeadersMultiMap remove(final String name) {
    return remove((CharSequence) name);
  }

  @Override
  public HeadersMultiMap set(CharSequence name, CharSequence value) {
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    remove0(h, i, name);
    if (value != null) {
      add0(h, i, name, value);
    }
    modCount++;
    return this;
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
    modCount++;
    return this;
  }

  @Override
  public HeadersMultiMap set(String name, Iterable values) {
    return set((CharSequence) name, values);
  }

  @Override
  public boolean containsValue(CharSequence name, CharSequence value, boolean ignoreCase) {
    return contains(name, value, false, ignoreCase);
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
    return contains(name, value, true, ignoreCase);
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
    MapEntry[] etr = entries;
    if (etr != null) {
      HeadersMultiMap.MapEntry e = etr[i];
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
    }
    return values == null ? Collections.emptyList() : Collections.unmodifiableList(values);
  }

  @Override
  public List<String> getAll(String name) {
    return getAll((CharSequence) name);
  }

  @Override
  public void forEach(Consumer<? super Map.Entry<String, String>> action) {
    for (MapEntry c = head;c != null;c = c.after) {
      action.accept(c.stringEntry());
    }
  }

  @Override
  public void forEach(BiConsumer<String, String> action) {
    for (MapEntry c = head;c != null;c = c.after) {
      action.accept(c.getKey().toString(), c.getValue().toString());
    }
  }

  @Override
  public List<Map.Entry<String, String>> entries() {
    return MultiMap.super.entries();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    MapEntry h = head;
    if (h == null) {
      return Collections.emptyIterator();
    } else {
      return new Iterator<>() {

        private int numberOfIterations = 0;
        private EntryWrapper next = wrapEntry(head);

        @Override
        public boolean hasNext() {
          return next != null;
        }

        @Override
        public Map.Entry<String, String> next() {
          EntryWrapper n = next;
          if (n == null) {
            throw new NoSuchElementException();
          }
          if (modCount != n.expectedModCount) {
            throw new ConcurrentModificationException();
          }
          next = wrapEntry(n.entry.after);
          return n;
        }

        private EntryWrapper wrapEntry(MapEntry e) {
          return e != null ? new EntryWrapper(numberOfIterations++, e, modCount) : null;
        }

        class EntryWrapper implements Map.Entry<String, String> {

          private final int index;
          private int expectedModCount;
          private MapEntry entry;

          public EntryWrapper(int index, MapEntry entry, int expectedModCount) {
            this.index = index;
            this.entry = entry;
            this.expectedModCount = expectedModCount;
          }

          @Override
          public String getKey() {
            return entry.key.toString();
          }

          @Override
          public String getValue() {
            return entry.value.toString();
          }

          @Override
          public String setValue(String value) {
            if (readOnly) {
              throw new IllegalStateException("Read only");
            }
            if (expectedModCount != modCount) {
              throw new ConcurrentModificationException();
            }
            if (ref != null) {
              copyOnWrite();
              int i = 0;
              MapEntry e = head;
              for (;i < index; i++) {
                e = e.after;
              }
              entry = e;
              MapEntry c = null;
              while (++i < numberOfIterations) {
                e = e.after;
                c = e;
              }
              int modCountValue = ++modCount;
              next = c != null ? new EntryWrapper(numberOfIterations, c, modCountValue) : null;
              expectedModCount = modCountValue;
            }
            return entry.setValue(value).toString();
          }

          @Override
          public String toString() {
            return getKey() + "=" + getValue();
          }
        }
      };
    }
  }

  @Override
  public boolean isEmpty() {
    return head == null;
  }

  @Override
  public Set<String> names() {
    Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    for (MapEntry c = head;c != null;c = c.after) {
      names.add(c.getKey().toString());
    }
    return names;
  }

  @Override
  public HeadersMultiMap clear() {
    clear0();
    modCount++;
    return this;
  }

  private void clear0() {
    if (readOnly) {
      throw new IllegalStateException("Read only");
    } else {
      head = tail = null;
      if (ref != null) {
        entries = null;
      } else if (entries != null) {
        Arrays.fill(entries, null);
      }
    }
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
    String value = get(name);
    if (value == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
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
    return new Iterator<>() {
      HeadersMultiMap.MapEntry current = head;
      @Override
      public boolean hasNext() {
        return current != null;
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

  @Override
  public boolean isMutable() {
    return !readOnly;
  }

  @Override
  public HeadersMultiMap copy(boolean mutable) {
    if (readOnly) {
      if (mutable) {
        return new HeadersMultiMap(false, entries, head, validator, this);
      } else {
        return this;
      }
    } else if (ref == null) {
      return new HeadersMultiMap(!mutable, this);
    } else {
      if (mutable) {
        return new HeadersMultiMap(false, ref.entries, ref.head, ref.validator, ref);
      } else {
        return ref;
      }
    }
  }

  @Override
  public HeadersMultiMap copy() {
    return (HeadersMultiMap) MultiMap.super.copy();
  }

  public void encode(ByteBuf buf, boolean cache) {
    if (cache && readOnly) {
      Reference<byte[]> r = renderedBytesRef;
      byte[] bytes;
      if (r == null || (bytes = r.get()) == null) {
        int from = buf.writerIndex();
        encode0(buf);
        int to = buf.writerIndex();
        bytes = new byte[to - from];
        buf.getBytes(from, bytes);
        renderedBytesRef = new SoftReference<>(bytes);
      } else {
        buf.writeBytes(bytes);
      }
    } else {
      HeadersMultiMap r = ref;
      if (r != null) {
        r.encode(buf, cache);
      } else {
        encode0(buf);
      }
    }
  }

  private void encode0(ByteBuf buf) {
    for (MapEntry c = head;c != null;c = c.after) {
      encodeHeader(c.key, c.value, buf);
    }
  }

  private final class MapEntry implements Map.Entry<CharSequence, CharSequence> {

    private final int hash;
    private final CharSequence key;
    private CharSequence value;
    private HeadersMultiMap.MapEntry next;
    private HeadersMultiMap.MapEntry before, after;

    MapEntry(int hash, CharSequence key, CharSequence value) {
      this.hash = hash;
      this.key = key;
      this.value = value;
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
      assert !readOnly && ref == null;
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

  private void addAll(HeadersMultiMap headers) {
    for (MapEntry c = headers.head;c != null;c = c.after) {
      int h = AsciiString.hashCode(c.key);
      int i = h & 0x0000000F;
      add0(h, i, c.key, c.value);
    }
  }

  private void setAll(Iterable<Map.Entry<String, String>> map) {
    clear0();
    for (Map.Entry<String, String> entry: map) {
      String key = entry.getKey();
      int h = AsciiString.hashCode(key);
      int i = h & 0x0000000F;
      add0(h, i, key, entry.getValue());
    }
  }

  private void checkMutable() {
    if (readOnly) {
      throw new IllegalStateException("Read only");
    } else if (ref != null) {
      copyOnWrite();
    }
  }

  private void copyOnWrite() {
    HeadersMultiMap state = ref;
    assert state != null;
    head = null;
    entries = new MapEntry[16];
    ref = null;
    addAll(state);
  }

  private boolean contains(CharSequence name, CharSequence value, boolean equals, boolean ignoreCase) {
    MapEntry[] etr = entries;
    if (etr != null) {
      int h = AsciiString.hashCode(name);
      int i = h & 0x0000000F;
      HeadersMultiMap.MapEntry e = etr[i];
      while (e != null) {
        CharSequence key = e.key;
        if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
          CharSequence other = e.getValue();
          if (equals) {
            if ((ignoreCase && AsciiString.contentEqualsIgnoreCase(value, other)) || (!ignoreCase && AsciiString.contentEquals(value, other))) {
              return true;
            }
          } else {
            if (contains(value, ignoreCase, other)) {
              return true;
            }
          }
        }
        e = e.next;
      }
    }
    return false;
  }

  private static boolean contains(CharSequence s, boolean ignoreCase, CharSequence sub) {
    int prev = 0;
    while (true) {
      final int idx = AsciiString.indexOf(sub, ',', prev);
      int to;
      if (idx == -1) {
        to = sub.length();
      } else {
        to = idx;
      }
      while (to > prev && sub.charAt(to - 1) == ' ') {
        to--;
      }
      int from = prev;
      while (from < to && sub.charAt(from) == ' ') {
        from++;
      }
      int len = to - from;
      if (len > 0 && AsciiString.regionMatches(sub, ignoreCase, from, s, 0, len)) {
        return true;
      } else if (idx == -1) {
        break;
      }
      prev = idx + 1;
    }
    return false;
  }

  private void remove0(int h, int i, CharSequence name) {
    checkMutable();
    MapEntry[] etr = entries;
    if (etr == null) {
      return;
    }
    HeadersMultiMap.MapEntry e = etr[i];
    MapEntry prev = null;
    while (e != null) {
      MapEntry next = e.next;
      CharSequence key = e.key;
      if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
        if (prev == null) {
          etr[i] = next;
        } else {
          prev.next = next;
        }
        if (e.before == null) {
          head = e.after;
        } else {
          e.before.after = e.after;
        }
        if (e.after == null) {
          tail = e.before;
        } else {
          e.after.before = e.before;
        }
        e.after = null;
        e.before = null;
      } else {
        prev = e;
      }
      e = next;
    }
  }

  private void add0(int h, int i, final CharSequence name, final CharSequence value) {
    checkMutable();
    if (validator != null) {
      validator.accept(name, value);
    }
    if (entries == null) {
      entries = new MapEntry[16];
    }

    // Update the hash table.
    HeadersMultiMap.MapEntry e = entries[i];
    HeadersMultiMap.MapEntry newEntry;
    entries[i] = newEntry = new HeadersMultiMap.MapEntry(h, name, value);
    newEntry.next = e;

    // Update the linked list.
    if (head == null) {
      head = tail = newEntry;
    } else {
      newEntry.before = tail;
      tail.after = newEntry;
      tail = newEntry;
    }
  }

  private CharSequence get0(CharSequence name) {
    int h = AsciiString.hashCode(name);
    int i = h & 0x0000000F;
    CharSequence value = null;
    MapEntry[] etr = entries;
    if (etr != null) {
      HeadersMultiMap.MapEntry e = etr[i];
      while (e != null) {
        CharSequence key = e.key;
        if (e.hash == h && (name == key || AsciiString.contentEqualsIgnoreCase(name, key))) {
          value = e.getValue();
        }
        e = e.next;
      }
    }
    return value;
  }

  /**
   * Convert the {@code value} to a non null {@code CharSequence}
   * @param value the value
   * @return the char sequence
   */
  public static CharSequence toValidCharSequence(Object value) {
    if (value instanceof CharSequence) {
      return (CharSequence) value;
    } else {
      // Throws NPE
      return value.toString();
    }
  }

  private static void encodeHeader(CharSequence name, CharSequence value, ByteBuf buf) {
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
}
