/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.json.pointer.impl;

import io.vertx.core.json.pointer.JsonPointer;
import io.vertx.core.json.pointer.JsonPointerIterator;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * @author Francesco Guardiani @slinkydeveloper
 */
public class JsonPointerImpl implements JsonPointer {

  final public static Pattern VALID_POINTER_PATTERN = Pattern.compile("^(/(([^/~])|(~[01]))*)*$");

  URI startingUri;

  // Empty means a pointer to root
  List<String> decodedTokens;

  public JsonPointerImpl(URI uri) {
    this.startingUri = removeFragment(uri);
    this.decodedTokens = parse(uri.getFragment());
  }

  public JsonPointerImpl(String pointer) {
    this.startingUri = URI.create("#");
    this.decodedTokens = parse(pointer);
  }

  public JsonPointerImpl() {
    this.startingUri = URI.create("#");
    this.decodedTokens = parse(null);
  }

  protected JsonPointerImpl(URI startingUri, List<String> decodedTokens) {
    this.startingUri = startingUri;
    this.decodedTokens = new ArrayList<>(decodedTokens);
  }

  private ArrayList<String> parse(String pointer) {
    if (pointer == null || "".equals(pointer)) {
      return new ArrayList<>();
    }
    if (VALID_POINTER_PATTERN.matcher(pointer).matches()) {
      return Arrays
          .stream(pointer.split("\\/", -1))
          .skip(1) //Ignore first element
          .map(this::unescape)
          .collect(Collectors.toCollection(ArrayList::new));
    } else
      throw new IllegalArgumentException("The provided pointer is not a valid JSON Pointer");
  }

  private String escape(String path) {
    return path.replace("~", "~0")
        .replace("/", "~1");
  }

  private String unescape(String path) {
    return path.replace("~1", "/") // https://tools.ietf.org/html/rfc6901#section-4
        .replace("~0", "~");
  }

  @Override
  public boolean isRootPointer() {
    return decodedTokens.size() == 0;
  }

  @Override
  public boolean isLocalPointer() {
    return startingUri == null || startingUri.getSchemeSpecificPart() == null || startingUri.getSchemeSpecificPart().isEmpty();
  }

  @Override
  public boolean isParent(JsonPointer c) {
    JsonPointerImpl child = (JsonPointerImpl) c;
    return child != null &&
        (child.getURIWithoutFragment() == null && this.getURIWithoutFragment() == null || child.getURIWithoutFragment().equals(this.getURIWithoutFragment())) &&
        decodedTokens.size() < child.decodedTokens.size() &&
        IntStream.range(0, decodedTokens.size())
            .mapToObj(i -> this.decodedTokens.get(i).equals(child.decodedTokens.get(i)))
            .reduce(Boolean::logicalAnd).orElse(true);
  }

  @Override
  public String toString() {
    if (isRootPointer())
      return "";
    else
      return "/" + String.join("/", decodedTokens.stream().map(this::escape).collect(Collectors.toList()));
  }

  @Override
  public URI toURI() {
    if (isRootPointer()) {
      return replaceFragment(this.startingUri, "");
    } else
      return replaceFragment(
          this.startingUri,
          "/" + String.join("/", decodedTokens.stream().map(this::escape).collect(Collectors.toList()))
      );
  }

  @Override
  public URI getURIWithoutFragment() {
    return startingUri;
  }

  @Override
  public JsonPointer append(String path) {
    decodedTokens.add(path);
    return this;
  }

  @Override
  public JsonPointer append(int i) {
    return this.append(Integer.toString(i));
  }

  @Override
  public JsonPointer append(List<String> paths) {
    decodedTokens.addAll(paths);
    return this;
  }

  @Override
  public JsonPointer append(JsonPointer pointer) {
    decodedTokens.addAll(((JsonPointerImpl)pointer).decodedTokens);
    return this;
  }

  @Override
  public JsonPointer parent() {
    if (!this.isRootPointer()) decodedTokens.remove(decodedTokens.size() - 1);
    return this;
  }

  @Override
  public JsonPointer copy() {
    return new JsonPointerImpl(this.startingUri, this.decodedTokens);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    JsonPointerImpl that = (JsonPointerImpl) o;
    return Objects.equals(startingUri, that.startingUri) &&
        Objects.equals(decodedTokens, that.decodedTokens);
  }

  @Override
  public int hashCode() {
    return Objects.hash(startingUri, decodedTokens);
  }

  @Override
  public Object queryOrDefault(Object value, JsonPointerIterator iterator, Object defaultValue) {
    // I should threat this as a special condition because the empty string can be a json obj key!
    if (isRootPointer())
      return (iterator.isNull(value)) ? defaultValue : value;
    else {
      value = walkTillLastElement(value, iterator, false, null);
      String lastKey = decodedTokens.get(decodedTokens.size() - 1);
      if (iterator.isObject(value)) {
        Object finalValue = iterator.getObjectParameter(value, lastKey, false);
        return (!iterator.isNull(finalValue)) ? finalValue : defaultValue;
      } else if (iterator.isArray(value) && !"-".equals(lastKey)) {
        try {
          Object finalValue = iterator.getArrayElement(value, Integer.parseInt(lastKey));
          return (!iterator.isNull(finalValue)) ? finalValue : defaultValue;
        } catch (NumberFormatException e) {
          return defaultValue;
        }
      } else
        return defaultValue;
    }
  }

  @Override
  public List<Object> tracedQuery(Object objectToQuery, JsonPointerIterator iterator) {
    List<Object> list = new ArrayList<>();
    if (isRootPointer() && !iterator.isNull(objectToQuery))
      list.add(objectToQuery);
    else {
      Object lastValue = walkTillLastElement(objectToQuery, iterator, false, list::add);
      if (!iterator.isNull(lastValue))
        list.add(lastValue);
      String lastKey = decodedTokens.get(decodedTokens.size() - 1);
      if (iterator.isObject(lastValue)) {
        lastValue = iterator.getObjectParameter(lastValue, lastKey, false);
      } else if (iterator.isArray(lastValue) && !"-".equals(lastKey)) {
        try {
          lastValue = iterator.getArrayElement(lastValue, Integer.parseInt(lastKey));
        } catch (NumberFormatException e) { }
      }
      if (!iterator.isNull(lastValue))
        list.add(lastValue);
    }
    return list;
  }

  @Override
  public Object write(Object valueToWrite, JsonPointerIterator iterator, Object newElement, boolean createOnMissing) {
    if (isRootPointer()) {
      return iterator.isNull(valueToWrite) ? null : newElement;
    } else {
      Object walkedValue = walkTillLastElement(valueToWrite, iterator, createOnMissing, null);
      if (writeLastElement(walkedValue, iterator, newElement))
        return valueToWrite;
      else
        return null;
    }
  }

  private Object walkTillLastElement(Object value, JsonPointerIterator iterator, boolean createOnMissing, Consumer<Object> onNewValue) {
    for (int i = 0; i < decodedTokens.size() - 1; i++) {
      String k = decodedTokens.get(i);
      if (i == 0 && "".equals(k)) {
        continue; // Avoid errors with root empty string
      } else if (iterator.isObject(value)) {
        if (onNewValue != null) onNewValue.accept(value);
        value = iterator.getObjectParameter(value, k, createOnMissing);
      } else if (iterator.isArray(value)) {
        if (onNewValue != null) onNewValue.accept(value);
        try {
          value = iterator.getArrayElement(value, Integer.parseInt(k));
          if (iterator.isNull(value) && createOnMissing) {
            value = iterator.getObjectParameter(value, k, true);
          }
        } catch (NumberFormatException e) {
          value = null;
        }
      } else {
        return null;
      }
    }
    return value;
  }

  private boolean writeLastElement(Object valueToWrite, JsonPointerIterator iterator, Object newElement) {
    String lastKey = decodedTokens.get(decodedTokens.size() - 1);
    if (iterator.isObject(valueToWrite)) {
      return iterator.writeObjectParameter(valueToWrite, lastKey, newElement);
    } else if (iterator.isArray(valueToWrite)) {
      if ("-".equals(lastKey)) { // Append to end
        return iterator.appendArrayElement(valueToWrite, newElement);
      } else { // We have a index
        try {
          return iterator.writeArrayElement(valueToWrite, Integer.parseInt(lastKey), newElement);
        } catch (NumberFormatException e) {
          return false;
        }
      }
    } else
      return false;
  }

  private URI removeFragment(URI oldURI) {
    return replaceFragment(oldURI, null);
  }

  private URI replaceFragment(URI oldURI, String fragment) {
    try {
      if (oldURI != null) {
        return new URI(oldURI.getScheme(), oldURI.getSchemeSpecificPart(), fragment);
      } else return new URI(null, null, fragment);
    } catch (URISyntaxException e) {
      e.printStackTrace();
      return null;
    }
  }
}
