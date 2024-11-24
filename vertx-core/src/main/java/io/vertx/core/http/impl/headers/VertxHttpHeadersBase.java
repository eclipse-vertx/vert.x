/*
package io.vertx.core.http.impl.headers;

import io.netty.handler.codec.Headers;
import io.vertx.core.MultiMap;

import java.util.*;
import java.util.stream.Collectors;

*/
/**
 * @author <a href="mailto:zolfaghari19@gmail.com">Iman Zolfaghari</a>
 *//*

public abstract class VertxHttpHeadersBase<H extends Headers<CharSequence, CharSequence, H>> implements VertxHttpHeaders {
  protected H headers;
  protected final HttpHeadersAdaptor<H>headersAdaptor;

  public VertxHttpHeadersBase(H headers, HttpHeadersAdaptor<H>headersAdaptor) {
    this.headers = headers;
    this.headersAdaptor = headersAdaptor;
  }

  @Override
  public H getHeaders() {
    return headers;
  }

  @Override
  public MultiMap add(CharSequence name, CharSequence value) {
    this.headersAdaptor.add(name, value);
    return this;
  }

  @Override
  public String get(String name) {
    return headersAdaptor.get(name);
  }

  @Override
  public VertxHttpHeadersBase<H> set(String name, String value) {
    this.headersAdaptor.set(name, value);
    return this;
  }

  @Override
  public VertxHttpHeadersBase<H> add(String name, String value) {
    this.headersAdaptor.add(name, value);
    return this;
  }

  @Override
  public boolean contains(CharSequence name, CharSequence value) {
    return headers.contains(name, value);
  }

  @Override
  public VertxHttpHeadersBase<H> remove(String name) {
    headersAdaptor.remove(name);
    return this;
  }

  public Iterator<Map.Entry<String, String>> iterator(){
    return headersAdaptor.iterator();
  }

  @Override
  public String get(CharSequence name) {
    return headersAdaptor.get(name);
  }

  @Override
  public boolean contains(CharSequence name) {
    return this.headersAdaptor.contains(name);
  }

  @Override
  public List<String> getAll(String name) {
    return headersAdaptor.getAll(name);
  }

  @Override
  public List<String> getAll(CharSequence name) {
    return headersAdaptor.getAll(name);
  }

  @Override
  public boolean contains(String name) {
    return headersAdaptor.contains(name);
  }

  @Override
  public boolean isEmpty() {
    return headersAdaptor.isEmpty();
  }

  @Override
  public Set<String> names() {
    return this.headersAdaptor.names();
  }

  @Override
  public VertxHttpHeadersBase<H> add(String name, Iterable<String> values) {
    this.headersAdaptor.add(name, values);
    return this;
  }

  @Override
  public MultiMap add(CharSequence name, Iterable<CharSequence> values) {
    this.headersAdaptor.add(name, values);
    return this;
  }

  @Override
  public MultiMap addAll(MultiMap map) {
    headersAdaptor.addAll(map);
    return this;
  }

  @Override
  public MultiMap addAll(Map<String, String> headers) {
    headersAdaptor.addAll(headers);
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, CharSequence value) {
    this.headersAdaptor.set(name, value);
    return this;
  }

  @Override
  public MultiMap set(String name, Iterable<String> values) {
    this.headersAdaptor.set(name, values);
    return this;
  }

  @Override
  public MultiMap set(CharSequence name, Iterable<CharSequence> values) {
    this.headersAdaptor.set(name, values);
    return this;
  }

  @Override
  public MultiMap setAll(MultiMap map) {
    headersAdaptor.setAll(map);
    return this;
  }

  @Override
  public MultiMap setAll(Map<String, String> headers) {
    headersAdaptor.setAll(headers);
    return this;
  }

  @Override
  public MultiMap remove(CharSequence name) {
    headersAdaptor.remove(name);
    return this;
  }

  @Override
  public MultiMap clear() {
    headersAdaptor.clear();
    return this;
  }

  @Override
  public int size() {
    return headersAdaptor.size();
  }
}
*/
