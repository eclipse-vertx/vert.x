/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

import io.vertx.core.net.Address;

import java.util.Objects;

final class ResolvingKey<K, A extends Address> {

  final K key;
  final A address;

  ResolvingKey(K key, A address) {
    this.key = Objects.requireNonNull(key);
    this.address = Objects.requireNonNull(address);
  }

  @Override
  public int hashCode() {
    return key.hashCode() ^ address.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this){
      return true;
    }
    if (obj instanceof ResolvingKey) {
      ResolvingKey that = (ResolvingKey) obj;
      return key.equals(that.key) && address.equals(that.address);
    }
    return false;
  }

  @Override
  public String toString() {
    return "ResolvingKey(key=" + key + ",address=" + address + ")";
  }
}
