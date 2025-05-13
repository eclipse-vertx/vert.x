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

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.Completable;
import io.vertx.core.eventbus.impl.clustered.selector.*;
import io.vertx.core.spi.cluster.ClusteredNode;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationUpdateEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Node selector implementation that preserves the ordering of select operations.
 */
public class DefaultNodeSelector implements NodeSelector {

  private ClusteredNode clusterManager;
  private final ConcurrentMap<String, Node> entries = new ConcurrentHashMap<>();

  private interface Op<T> {
    Op<String> SEND = RoundRobinSelector::selectForSend;
    Op<Iterable<String>> PUBLISH = RoundRobinSelector::selectForPublish;
    T select(RoundRobinSelector selector);
  }

  private static class Node {

    final AtomicInteger wip = new AtomicInteger(1);
    final Queue<Action> queue = PlatformDependent.newMpscQueue();
    Object value;

    private void signal(Object value, int amount) {
      while (amount > 0) {
        for (int i = 0;i < amount;i++) {
          Action a = queue.poll();
          assert a != null;
          if (a instanceof Select) {
            Select<?> s = (Select<?>) a;
            if (value instanceof RoundRobinSelector) {
              s.resolve((RoundRobinSelector) value);
            } else {
              s.fail((Throwable) value);
            }
          } else if (a instanceof Update) {
            value = ((Update)a).selector;
          } else {
            throw new UnsupportedOperationException();
          }
        }
        // We write the value before writing wip to ensure visibility since wip is read
        this.value = value;
        amount = wip.addAndGet(-amount);
      }
    }
  }

  private interface Action {
  }

  private static class Update implements Action {
    // null means delete
    final RoundRobinSelector selector;
    Update(RoundRobinSelector selector) {
      this.selector = selector;
    }
  }

  private static class Select<T> implements Action {
    final Op<T> op;
    final Completable<T> callback;
    Select(Op<T> op, Completable<T> callback) {
      this.op = op;
      this.callback = callback;
    }
    void resolve(RoundRobinSelector selector) {
      callback.succeed(op.select(selector));
    }
    void fail(Throwable err) {
      callback.fail(err);
    }
  }

  private <T> void selectFor(String address, Op<T> op, Completable<T> callback) {
    Node node = entries.get(address);
    if (node == null) {
      node = new Node();
      node.queue.add(new Select<>(op, callback));
      Node phantom = entries.putIfAbsent(address, node);
      if (phantom != null) {
        node = phantom;
      } else {
        // Obtained ownership
        initializeNode(node, address);
        return;
      }
    }
    if (node.wip.get() == 0) {
      // wip == 0 implies we can safely read a value
      Object v = node.value;
      if (v instanceof RoundRobinSelector) {
        callback.succeed(op.select((RoundRobinSelector) v));
      } else {
        callback.fail((Throwable) v);
      }
    } else {
      node.queue.add(new Select<>(op, callback));
      int amount = node.wip.incrementAndGet();
      if (amount == 1) {
        // Race case obtained incidentally ownership
        // it happens when a concurrent signal operation finished after we enqueued our select
        // we can read the node value (since amount was 0) and signal
        node.signal(node.value, amount);
      }
    }
  }

  private void initializeNode(Node node, String address) {
    clusterManager.getRegistrations(address, (res, err) -> {
      if (err == null) {
        succeed(node, address, res);
      } else {
        fail(node, address, err);
      }
    });
  }

  private void succeed(Node node, String address, List<RegistrationInfo> registrations) {
    List<String> accessible = computeAccessible(registrations);
    RoundRobinSelector selector = data(accessible);
    if (selector != null) {
      node.signal(selector, node.wip.get());
    } else {
      if (entries.remove(address, node)) {
        node.signal(NullRoundRobinSelector.INSTANCE, node.wip.get());
      }
    }
  }

  private void fail(Node node, String address, Throwable cause) {
    entries.remove(address, node);
    node.signal(cause, node.wip.get());
  }

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

  private RoundRobinSelector data(List<String> nodeIds) {
    if (nodeIds == null || nodeIds.isEmpty()) {
      return null;
    } else {
      Map<String, Weight> weights = computeWeights(nodeIds);
      RoundRobinSelector selector;
      if (isEvenlyDistributed(weights)) {
        selector = new SimpleRoundRobinSelector(new ArrayList<>(weights.keySet()));
      } else {
        selector = new WeightedRoundRobinSelector(weights);
      }
      return selector;
    }
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
    List<String> accessible = computeAccessible(event.registrations());
    RoundRobinSelector selector = data(accessible);
    if (selector != null) {
      Node node = entries.get(address);
      if (node != null) {
        node.queue.add(new Update(selector));
        int amount = node.wip.incrementAndGet();
        if (amount == 1) {
          node.signal(selector, amount);
        }
      } else {
        // ????
      }
    } else {
      entries.remove(address);
    }
  }
}
