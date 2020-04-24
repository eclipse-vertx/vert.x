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

import io.vertx.core.Context;
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
import io.vertx.core.spi.cluster.RegistrationListener;

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
  public void chooseNodes(Message<?> message, Promise<List<String>> promise, Context ctx) {
    ContextInternal context = (ContextInternal) Vertx.currentContext();
    if (ctx != context) {
      // This implementation has to run on the ctx to guarantee message delivery ordering
      ctx.runOnContext(v -> chooseNodes(message, promise, ctx));
      return;
    }

    String address = message.address();

    Queue<Waiter> waiters = getWaiters(context, address);

    NodeSelector selector = selectors.get(address);
    if (selector != null && waiters.isEmpty()) {
      promise.complete(selector.selectNodes(message));
      return;
    }

    waiters.add(new Waiter(message, promise));
    if (waiters.size() == 1) {
      dequeueWaiters(context, address);
    }
  }

  @SuppressWarnings("unchecked")
  private Queue<Waiter> getWaiters(ContextInternal context, String address) {
    Map<String, Queue<Waiter>> map = (Map<String, Queue<Waiter>>) context.contextData().computeIfAbsent(this, ctx -> new HashMap<>());
    return map.computeIfAbsent(address, a -> new ArrayDeque<>());
  }

  @SuppressWarnings("unchecked")
  private void removeWaiters(ContextInternal context, String address) {
    context.contextData().compute(this, (k, v) -> {
      Map<String, Queue<Waiter>> map = (Map<String, Queue<Waiter>>) v;
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

    Queue<Waiter> waiters = getWaiters(context, address);

    Waiter waiter;
    for (int i = 0; ; i++) {
      if (i == 10) {
        // Give a chance to other tasks every 10 iterations
        context.runOnContext(v -> dequeueWaiters(context, address));
      }
      Waiter peeked = waiters.peek();
      if (peeked == null) {
        throw new IllegalStateException();
      }
      NodeSelector selector = selectors.get(address);
      if (selector != null) {
        peeked.promise.complete(selector.selectNodes(peeked.message));
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

    Promise<RegistrationListener> promise = context.promise();
    clusterManager.registrationListener(address, promise);
    promise.future().onComplete(ar -> {
      if (ar.succeeded()) {
        registrationListenerCreated(context, address, ar.result());
      } else {
        waiter.promise.fail(ar.cause());
        removeFirstAndDequeueWaiters(context, address);
      }
    });
  }

  private void registrationListenerCreated(ContextInternal context, String address, RegistrationListener listener) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }

    Queue<Waiter> waiters = getWaiters(context, address);

    Waiter waiter = waiters.peek();
    if (waiter == null) {
      throw new IllegalStateException();
    }

    if (listener.initialState().isEmpty()) {
      waiter.promise.complete(NodeSelector.EMPTY_SELECTOR.selectNodes(waiter.message));
      removeFirstAndDequeueWaiters(context, address);
      return;
    }

    NodeSelector candidate = NodeSelector.create(nodeId, listener.initialState());
    NodeSelector previous = selectors.putIfAbsent(address, candidate);
    NodeSelector current = previous != null ? previous : candidate;

    waiter.promise.complete(current.selectNodes(waiter.message));
    removeFirstAndDequeueWaiters(context, address);

    if (previous == null) {
      startListening(address, listener);
    }
  }

  private void removeFirstAndDequeueWaiters(ContextInternal context, String address) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }

    Queue<Waiter> waiters = getWaiters(context, address);
    waiters.remove();
    if (!waiters.isEmpty()) {
      context.runOnContext(v -> dequeueWaiters(context, address));
    }
  }

  private void startListening(String address, RegistrationListener listener) {
    listener
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

  private static class Waiter {
    final Message<?> message;
    final Promise<List<String>> promise;

    private Waiter(Message<?> message, Promise<List<String>> promise) {
      this.message = message;
      this.promise = promise;
    }
  }
}
