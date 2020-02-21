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

package io.vertx.core.spi.cluster.impl;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.cluster.DeliveryStrategy;
import io.vertx.core.spi.cluster.RegistrationInfo;
import io.vertx.core.spi.cluster.RegistrationStream;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default {@link DeliveryStrategy}.
 *
 * @author Thomas Segismont
 */
public class DefaultDeliveryStrategy implements DeliveryStrategy {

  private static final Logger logger = LoggerFactory.getLogger(DefaultDeliveryStrategy.class);

  private final ConcurrentMap<String, NodeSelector> selectors = new ConcurrentHashMap<>();

  private ClusterManager clusterManager;
  private String nodeId;

  @Override
  public void setVertx(VertxInternal vertx) {
    clusterManager = vertx.getClusterManager();
  }

  @Override
  public void eventBusStarted() {
    nodeId = clusterManager.getNodeId();
  }

  @Override
  public Future<List<String>> chooseNodes(Message<?> message) {
    return selector(message.address()).map(selector -> selector.selectNodes(message));
  }

  public Future<NodeSelector> selector(String address) {
    ContextInternal context = (ContextInternal) Vertx.currentContext();
    Objects.requireNonNull(context, "Method must be invoked on a Vert.x thread");

    Queue<Promise<NodeSelector>> waiters = getWaiters(context, address);

    NodeSelector selector = selectors.get(address);
    if (selector != null && waiters.isEmpty()) {
      return context.succeededFuture(selector);
    }

    Promise<NodeSelector> promise = context.promise();

    waiters.add(promise);
    if (waiters.size() == 1) {
      dequeueWaiters(context, address);
    }
    return promise.future();
  }

  @SuppressWarnings("unchecked")
  private Queue<Promise<NodeSelector>> getWaiters(ContextInternal context, String address) {
    Map<String, Queue<Promise<NodeSelector>>> map = (Map<String, Queue<Promise<NodeSelector>>>) context.contextData().computeIfAbsent(this, ctx -> new HashMap<>());
    return map.computeIfAbsent(address, a -> new ArrayDeque<>());
  }

  @SuppressWarnings("unchecked")
  private void removeWaiters(ContextInternal context, String address) {
    context.contextData().compute(context, (k, v) -> {
      Map<String, Queue<Promise<NodeSelector>>> map = (Map<String, Queue<Promise<NodeSelector>>>) v;
      if (map == null) {
        throw new IllegalStateException();
      }
      map.remove(address);
      return !map.isEmpty() ? map : null;
    });
  }

  private void dequeueWaiters(ContextInternal context, String address) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }

    Queue<Promise<NodeSelector>> waiters = getWaiters(context, address);

    Promise<NodeSelector> waiter;
    for (int i = 0; ; i++) {
      if (i == 10) {
        // Give a chance to other tasks every 10 iterations
        context.runOnContext(v -> dequeueWaiters(context, address));
      }
      Promise<NodeSelector> peeked = waiters.peek();
      if (peeked == null) {
        throw new IllegalStateException();
      }
      NodeSelector selector = selectors.get(address);
      if (selector != null) {
        peeked.complete(selector);
        waiters.remove();
        if (waiters.isEmpty()) {
          removeWaiters(context, address);
          return;
        }
      } else {
        waiter = peeked;
        break;
      }
    }

    clusterManager.registrationListener(address)
      .onFailure(t -> {
        waiter.fail(t);
        removeFirstAndDequeueWaiters(context, address);
      })
      .onSuccess(stream -> registrationListenerCreated(context, address, stream));
  }

  private void registrationListenerCreated(ContextInternal context, String address, RegistrationStream registrationStream) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }

    Queue<Promise<NodeSelector>> waiters = getWaiters(context, address);

    Promise<NodeSelector> waiter = waiters.peek();
    if (waiter == null) {
      throw new IllegalStateException();
    }

    if (registrationStream.initialState().isEmpty()) {
      waiter.complete(NodeSelector.EMPTY_SELECTOR);
      removeFirstAndDequeueWaiters(context, address);
      return;
    }

    NodeSelector candidate = NodeSelector.create(nodeId, registrationStream.initialState());
    NodeSelector previous = selectors.putIfAbsent(address, candidate);
    NodeSelector current = previous != null ? previous : candidate;

    waiter.complete(current);
    removeFirstAndDequeueWaiters(context, address);

    if (previous == null) {
      startListening(address, registrationStream);
    }
  }

  private void removeFirstAndDequeueWaiters(ContextInternal context, String address) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }

    Queue<Promise<NodeSelector>> waiters = getWaiters(context, address);
    waiters.remove();
    if (!waiters.isEmpty()) {
      context.runOnContext(v -> dequeueWaiters(context, address));
    }
  }

  private void startListening(String address, RegistrationStream registrationStream) {
    registrationStream
      .handler(registrationInfos -> registrationsUpdated(address, registrationInfos))
      .exceptionHandler(t -> {
        logger.debug("Exception while listening to registration changes", t);
        removeSelector(address);
      })
      .endHandler(v -> removeSelector(address))
      .start();
  }

  private void registrationsUpdated(String address, List<RegistrationInfo> registrationInfos) {
    selectors.put(address, NodeSelector.create(nodeId, registrationInfos));
  }

  private void removeSelector(String address) {
    selectors.remove(address);
  }
}
