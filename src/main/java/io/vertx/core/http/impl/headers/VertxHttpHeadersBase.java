package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.vertx.core.MultiMap;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class VertxHttpHeadersBase<H extends Headers<CharSequence, CharSequence, H>> implements VertxHttpHeaders {
  protected H headers;

  public VertxHttpHeadersBase(H headers) {
    this.headers = headers;
  }

  @Override
  public H getHeaders() {
    return headers;
  }

  @Override
  public MultiMap add(CharSequence name, CharSequence value) {
    this.headers.add(String.valueOf(name), String.valueOf(value));
    return this;
  }

  @Override
  public String get(String name) {
    return String.valueOf(this.headers.get(name));
  }

  @Override
  public VertxHttpHeadersBase<H> set(String name, String value) {
    this.headers.set(name, value);
    return this;
  }

  @Override
  public VertxHttpHeadersBase<H> add(String name, String value) {
    this.headers.add(name, value);
    return this;
  }

  @Override
  public boolean contains(String name, String value) {
    return headers.contains(name, value);
  }

  @Override
  public VertxHttpHeadersBase<H> remove(String name) {
    headers.remove(name);
    return this;
  }

  @Override
  public Iterable<Map.Entry<CharSequence, CharSequence>> getIterable() {
    return headers;
  }

  @Override
  public String get(CharSequence name) {
    return String.valueOf(this.headers.get(name));
  }

  @Override
  public boolean contains(CharSequence name) {
    return this.headers.contains(String.valueOf(name));
  }

  @Override
  public List<String> getAll(String name) {
    return headers.getAll(name).stream().map(CharSequence::toString).collect(Collectors.toList());
  }

  @Override
  public List<String> getAll(CharSequence name) {
    return this.getAll(String.valueOf(name));
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
    return this.headers.names().stream().map(CharSequence::toString).collect(Collectors.toSet());
  }

  @Override
  public VertxHttpHeadersBase<H> add(String name, Iterable<String> values) {
    this.headers.add(name, values);
    return this;
  }

  @Override
  public MultiMap add(CharSequence name, Iterable<CharSequence> values) {
    this.headers.add(name, values);
    return this;
  }

  @Override
  public MultiMap addAll(MultiMap map) {
    map.iterator().forEachRemaining(entry -> this.headers.add(entry.getKey(), entry.getValue()));
    return this;
  }

  @Override
  public MultiMap addAll(Map<String, String> headers) {
    headers.forEach((key, value) -> this.headers.add(key, value));
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, CharSequence value) {
    this.headers.set(name, value);
    return this;
  }

  @Override
  public MultiMap set(String name, Iterable<String> values) {
    this.headers.set(name, values);
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, Iterable<CharSequence> values) {
    this.headers.set(name, values);
    return this;
  }

  @Override
  public MultiMap setAll(MultiMap map) {
    map.forEach((key, value) -> this.headers.set(key, value));
    return this;
  }

  @Override
  public MultiMap setAll(Map<String, String> headers) {
    headers.forEach((key, value) -> this.headers.set(key, value));
    return this;
  }

  @Override
  public MultiMap remove(CharSequence name) {
    headers.remove(name);
    return this;
  }

  @Override
  public MultiMap clear() {
    headers.clear();
    return this;
  }

  @Override
  public int size() {
    return headers.size();
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    Map<String, String> map = new HashMap<>();
    this.headers.forEach(entry -> map.put(String.valueOf(entry.getKey()), String.valueOf(entry.getValue())));
    return map.entrySet().iterator();
  }
}
