/*
 * Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.shareddata.impl;

import io.vertx.core.impl.ClusterSerializableUtils;
import io.vertx.core.impl.SerializableUtils;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.shareddata.ClusterSerializable;
import io.vertx.core.shareddata.Shareable;

import java.io.ObjectInputStream;
import java.io.Serializable;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
class Checker {

  private static final Logger log = LoggerFactory.getLogger(Checker.class);

  private static final Set<Class<?>> IMMUTABLE_TYPES = Stream.<Class<?>>builder()
    .add(String.class)
    .add(Integer.class)
    .add(Long.class)
    .add(Boolean.class)
    .add(Double.class)
    .add(Float.class)
    .add(Short.class)
    .add(Byte.class)
    .add(Character.class)
    .add(BigInteger.class)
    .add(BigDecimal.class)
    .build()
    .collect(toSet());

  static void checkType(Object obj) {
    Objects.requireNonNull(obj, "null not allowed for shareddata data structure");
    // All immutables and byte arrays are Serializable by the platform
    if (!(obj instanceof Serializable || obj instanceof Shareable || obj instanceof ClusterSerializable)) {
      throw new IllegalArgumentException("Invalid type for shareddata data structure: " + obj.getClass().getName());
    }
  }

  @SuppressWarnings("unchecked")
  static <T> T copyIfRequired(T obj) {
    Object result;
    if (obj == null) {
      // Happens with putIfAbsent
      result = null;
    } else if (IMMUTABLE_TYPES.contains(obj.getClass())) {
      result = obj;
    } else if (obj instanceof byte[]) {
      result = copyByteArray((byte[]) obj);
    } else if (obj instanceof Shareable) {
      result = ((Shareable) obj).copy();
    } else if (obj instanceof ClusterSerializable) {
      result = copyClusterSerializable((ClusterSerializable) obj);
    } else if (obj instanceof Serializable) {
      result = copySerializable(obj);
    } else {
      throw new IllegalStateException();
    }
    return (T) result;
  }

  private static byte[] copyByteArray(byte[] bytes) {
    byte[] copy = new byte[bytes.length];
    System.arraycopy(bytes, 0, copy, 0, bytes.length);
    return copy;
  }

  private static ClusterSerializable copyClusterSerializable(ClusterSerializable obj) {
    logDeveloperInfo(obj);
    return ClusterSerializableUtils.copy(obj);
  }

  private static void logDeveloperInfo(Object obj) {
    if (log.isDebugEnabled()) {
      log.debug("Copying " + obj.getClass() + " for shared data. Consider implementing " + Shareable.class + " for better performance.");
    }
  }

  private static Object copySerializable(Object obj) {
    logDeveloperInfo(obj);
    return SerializableUtils.fromBytes(SerializableUtils.toBytes(obj), ObjectInputStream::new);
  }
}
