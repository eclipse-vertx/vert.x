/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.endpoint.impl;

import io.vertx.core.net.endpoint.ServerEndpoint;
import io.vertx.core.net.endpoint.ServerSelector;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Consistent hashing selector.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ConsistentHashingSelector implements ServerSelector {

  private final List<? extends ServerEndpoint> endpoints;
  private final SortedMap<Long, ServerEndpoint> nodes;
  private final ServerSelector fallbackSelector;

  public ConsistentHashingSelector(List<? extends ServerEndpoint> endpoints, int numberOfVirtualNodes, ServerSelector fallbackSelector) {
    MessageDigest instance;
    try {
      instance = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new UnsupportedOperationException(e);
    }
    SortedMap<Long, ServerEndpoint> ring = new TreeMap<>();
    for (ServerEndpoint node : endpoints) {
      for (int idx = 0;idx < numberOfVirtualNodes;idx++) {
        String nodeId = node.key() + "-" + idx;
        long hash = hash(instance, nodeId.getBytes(StandardCharsets.UTF_8));
        ring.put(hash, node);
      }
    }

    this.nodes = ring;
    this.endpoints = endpoints;
    this.fallbackSelector = fallbackSelector;
  }

  // MD5 : 16 bytes
  private static long hash(MessageDigest md, byte[] data) {
    md.reset();
    md.update(data);
    byte[] digest = md.digest();
    byte b0 = (byte)(digest[0] ^ digest[8]);
    byte b1 = (byte)(digest[1] ^ digest[9]);
    byte b2 = (byte)(digest[2] ^ digest[10]);
    byte b3 = (byte)(digest[3] ^ digest[11]);
    byte b4 = (byte)(digest[4] ^ digest[12]);
    byte b5 = (byte)(digest[5] ^ digest[13]);
    byte b6 = (byte)(digest[6] ^ digest[14]);
    byte b7 = (byte)(digest[7] ^ digest[15]);
    return ((long)b0 << 56) | ((long)b1 << 48) | ((long)b2 << 40) | ((long)b3 << 32)
      | ((long)b4 << 24) | ((long)b5 << 16) | ((long)b6 << 8) | (long)b7;
  }

  @Override
  public int select() {
    return fallbackSelector.select();
  }

  @Override
  public int select(String key) {
    if (key == null) {
      throw new NullPointerException("No null routing key accepted");
    }
    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      throw new UnsupportedOperationException(e);
    }
    long hash = hash(md, key.getBytes(StandardCharsets.UTF_8));
    SortedMap<Long, ServerEndpoint> map = nodes.tailMap(hash);
    Long val;
    if (map.isEmpty()) {
      val = nodes.firstKey();
    } else {
      val = map.firstKey();
    }
    ServerEndpoint endpoint = nodes.get(val);
    return endpoints.indexOf(endpoint); // TODO IMPROVE THAT
  }
}
