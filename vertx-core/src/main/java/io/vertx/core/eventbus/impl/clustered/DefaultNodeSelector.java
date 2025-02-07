/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus.impl.clustered;

import io.vertx.core.Completable;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.impl.clustered.selector.*;
import io.vertx.core.spi.cluster.ClusteredNode;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Node selector implementation that preserves the ordering of select operations.
 */
public class DefaultNodeSelector implements NodeSelector {

  private ClusteredNode clusterManager;
  private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

  @Override
  public void init(ClusteredNode clusterManager) {
    this.clusterManager = clusterManager;
  }

  @Override
  public void selectForSend(String address, Completable<String> promise) {
    selectFor(address, Op.SEND, promise);
  }

  @Override
  public void selectForPublish(String address, Completable<Iterable<String>> promise) {
    selectFor(address, Op.PUBLISH, promise);
  }

  @Override
  public boolean wantsUpdatesFor(String address) {
    return entries.containsKey(address);
  }

  private interface Op<T> {
    Op<String> SEND = RoundRobinSelector::selectForSend;
    Op<Iterable<String>> PUBLISH = RoundRobinSelector::selectForPublish;
    Op<Object> NOOP = selector -> null;
    T selectWith(RoundRobinSelector selector);
  }

  private <T> void selectFor(String address, Op<T> op, Completable<T> promise) {
    while (true) {
      Entry entry = entries.get(address);
      if (entry == null) {
        WaiterEntry<T> head = new WaiterEntry<>(promise, op);
        Entry phantom = entries.putIfAbsent(address, head);
        if (phantom == null) {
          initialize(head, address, op);
          break;
        }
      } else if (entry instanceof WaiterEntry) {
        WaiterEntry<T> next = new WaiterEntry<>(promise, op, (WaiterEntry<?>) entry);
        if (entries.replace(address, entry, next)) {
          break;
        }
      } else if (entry instanceof SelectorEntry) {
        SelectorEntry re = (SelectorEntry) entry;
        promise.succeed(op.selectWith(re.selector));
        break;
      }
    }
  }

  private <T> void initialize(WaiterEntry<?> head, String address, Op<T> k) {
    Promise<List<RegistrationInfo>> getPromise = Promise.promise();
    clusterManager.getRegistrations(address, getPromise);
    getPromise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        succeed(head, address, ar.result(), k);
      } else {
        fail(head, address, ar.cause());
      }
    });
  }

  private void fail(WaiterEntry<?> head, String address, Throwable cause) {
    // Check the entry is valid for the assumed head
    Entry entry = entries.get(address);
    if (entry instanceof WaiterEntry<?>) {
      WaiterEntry<?> tail = (WaiterEntry<?>) entry;
      if (tail.head == head) {
        // Try remove
        if (entries.remove(address, tail)) {
          // Broadcast failure
          while (tail != null) {
            tail.waiter.fail(cause);
            tail = tail.prev;
          }
        }
      }
    }
  }

  private <T> void succeed(WaiterEntry<?> head, String address, List<RegistrationInfo> registrations, Op<T> k) {
    List<String> accessible = computeAccessible(registrations);
    RoundRobinSelector selector = data(accessible);
    while (true) {
      Entry entry = entries.get(address);
      if (entry == null) {
        break;
      } else if (entry instanceof WaiterEntry) {
        WaiterEntry<?> tail = (WaiterEntry<?>) entry;
        if (tail.head == head) {
          if (selector != null) {
            if (entries.replace(address, tail, WaiterEntry.NOOP)) {
              broadcastToWaiters(tail, selector);
              if (entries.replace(address, WaiterEntry.NOOP, new SelectorEntry(selector))) {
                break;
              } else {
                // Another waiter has been added during broadcast, spin again
                head = WaiterEntry.NOOP;
              }
            }
          } else {
            // No handlers
            if (entries.remove(address, tail)) {
              broadcastToWaiters((WaiterEntry<?>) entry, NullRoundRobinSelector.INSTANCE);
              break;
            }
          }
        } else {
          break;
        }
      } else {
        throw new UnsupportedOperationException("Does this case make sense " + entry);
      }
    }
  }

  private RoundRobinSelector data(List<String> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return null;
    }
    Map<String, Weight> weights = computeWeights(nodeIds);
    RoundRobinSelector selector;
    if (isEvenlyDistributed(weights)) {
      selector = new SimpleRoundRobinSelector(new ArrayList<>(weights.keySet()));
    } else {
      selector = new WeightedRoundRobinSelector(weights);
    }
    return selector;
  }

  private Map<String, Weight> computeWeights(List<String> nodeIds) {
    Map<String, Weight> weights = new HashMap<>();
    for (String nodeId : nodeIds) {
      weights.compute(nodeId, (s, weight) -> weight == null ? new Weight(0) : weight.increment());
    }
    return weights;
  }

  private boolean isEvenlyDistributed(Map<String, Weight> weights) {
    if (weights.size() > 1) {
      Weight previous = null;
      for (Weight weight : weights.values()) {
        if (previous != null && previous.value() != weight.value()) {
          return false;
        }
        previous = weight;
      }
    }
    return true;
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

  @Override
  public void eventBusStarted() {
  }

  @Override
  public void registrationsUpdated(RegistrationUpdateEvent event) {
    String address = event.address();
    while (true) {
      Entry entry = entries.get(address);
      if (entry == null) {
        break;
      } else if (entry instanceof WaiterEntry) {
        throw new UnsupportedOperationException("Is this case valid ?");
      } else {
        SelectorEntry re = (SelectorEntry) entry;
        List<String> accessible = computeAccessible(event.registrations());
        RoundRobinSelector selector = data(accessible);
        if (selector == null) {
          // Un-registration
          if (entries.remove(address, re)) {
            break;
          }
        } else {
          if (entries.replace(address, re, new SelectorEntry(selector))) {
            break;
          }
        }
      }
    }
  }

  private static abstract class Entry {
  }

  /**
   * An entry waiting for an address to be resolved.
   */
  private static class WaiterEntry<T> extends Entry {

    static final WaiterEntry<?> NOOP = new WaiterEntry<>((a, b) -> {}, Op.NOOP);

    private final Completable<T> waiter;
    private final WaiterEntry<?> prev;
    private final WaiterEntry<?> head;
    private final Op<T> op;
    private WaiterEntry(Completable<T> waiter, Op<T> op) {
      this.waiter = waiter;
      this.prev = null;
      this.op = op;
      this.head = this;
    }
    private WaiterEntry(Completable<T> waiter, Op<T> op, WaiterEntry<?> prev) {
      this.waiter = waiter;
      this.prev = prev;
      this.op = op;
      this.head = prev.head;
    }
    void complete(RoundRobinSelector selector) {
      waiter.succeed(op.selectWith(selector));
    }
  }

  /**
   * Terminal entry, no waiter entry should be added after a resolved entry is added to the list
   */
  private static class SelectorEntry extends Entry {
    private final RoundRobinSelector selector;
    private SelectorEntry(RoundRobinSelector selector) {
      this.selector = selector;
    }
  }

  private static void broadcastToWaiters(WaiterEntry<?> lastWaiter, RoundRobinSelector selector) {
    List<WaiterEntry<?>> waiters = new ArrayList<>();
    for (WaiterEntry<?> e = lastWaiter;e != null;e = e.prev) {
      waiters.add(e);
    }
    for (int idx = waiters.size() - 1;idx >= 0;idx--) {
      waiters.get(idx).complete(selector);
    }
  }
}
