/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.spi.cluster.impl.selector;

import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.RegistrationInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.BiConsumer;

/**
 * @author Thomas Segismont
 */
public class Selectors {

  private final ConcurrentMap<String, SelectorEntry> map = new ConcurrentHashMap<>(0);
  private final ClusterManager clusterManager;

  public Selectors(ClusterManager clusterManager) {
    this.clusterManager = clusterManager;
  }

  public <T> void withSelector(String address, Promise<T> promise, BiConsumer<Promise<T>, RoundRobinSelector> task) {
    SelectorEntry entry = map.compute(address, (addr, curr) -> {
      return curr == null ? new SelectorEntry() : (curr.isNotReady() ? curr.increment() : curr);
    });
    if (entry.isNotReady()) {
      if (entry.shouldInitialize()) {
        initialize(address);
      }
      entry.selectorPromise.future().onComplete(ar -> {
        if (ar.succeeded()) {
          task.accept(promise, ar.result());
        } else {
          promise.fail(ar.cause());
        }
      });
    } else {
      task.accept(promise, entry.selector);
    }
  }

  private void initialize(String address) {
    Promise<List<RegistrationInfo>> getPromise = Promise.promise();
    clusterManager.getRegistrations(address, getPromise);
    getPromise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        dataReceived(address, ar.result(), false);
      } else {
        SelectorEntry entry = map.remove(address);
        if (entry != null && entry.isNotReady()) {
          entry.selectorPromise.fail(ar.cause());
        }
      }
    });
  }

  public void dataReceived(String address, List<RegistrationInfo> registrations, boolean isUpdate) {
    List<String> accessible = computeAccessible(registrations);
    while (true) {
      SelectorEntry previous = map.get(address);
      if (previous == null || (isUpdate && previous.isNotReady())) {
        break;
      }
      SelectorEntry next = previous.data(accessible);
      if (next == null) {
        if (map.remove(address, previous)) {
          if (previous.isNotReady()) {
            previous.selectorPromise.complete(NullRoundRobinSelector.INSTANCE);
          }
          break;
        }
      } else {
        if (map.replace(address, previous, next)) {
          if (previous.isNotReady()) {
            previous.selectorPromise.complete(next.selector);
          }
          break;
        }
      }
    }
  }

  private List<String> computeAccessible(List<RegistrationInfo> registrations) {
    if (registrations == null || registrations.isEmpty()) {
      return Collections.emptyList();
    }
    ArrayList<String> list = new ArrayList<>(registrations.size());
    for (RegistrationInfo registration : registrations) {
      if (isAccessible(registration)) {
        String nodeId = registration.nodeId();
        list.add(nodeId);
      }
    }
    list.trimToSize();
    return list;
  }

  private boolean isAccessible(RegistrationInfo registrationInfo) {
    return !registrationInfo.localOnly() || clusterManager.getNodeId().equals(registrationInfo.nodeId());
  }

  public void dataLost() {
    for (String address : map.keySet()) {
      SelectorEntry entry = map.remove(address);
      if (entry.isNotReady()) {
        entry.selectorPromise.complete(NullRoundRobinSelector.INSTANCE);
      }
    }
  }

  public boolean hasEntryFor(String address) {
    return map.containsKey(address);
  }
}
