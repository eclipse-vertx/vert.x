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

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.cluster.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;

/**
 * Default {@link DeliveryStrategy}.
 *
 * @author Thomas Segismont
 */
public class DefaultDeliveryStrategy implements DeliveryStrategy {

  private final ConcurrentMap<String, NodeSelector> selectors = new ConcurrentHashMap<>();
  private final TaskQueue taskQueue = new TaskQueue();

  private ClusterManager clusterManager;
  private NodeInfo nodeInfo;

  @Override
  public void setVertx(VertxInternal vertx) {
    clusterManager = vertx.getClusterManager();
  }

  @Override
  public void setNodeInfo(NodeInfo nodeInfo) {
    this.nodeInfo = nodeInfo;
  }

  @Override
  public void chooseNodes(Message<?> message, Handler<AsyncResult<List<NodeInfo>>> handler) {
    ContextInternal context = (ContextInternal) Vertx.currentContext();
    Objects.requireNonNull(context, "Method must be invoked on a Vert.x thread");

    // It's not necessary to synchronize access to this queue because the method is invoked on a standard or worker context
    @SuppressWarnings("unchecked")
    Queue<Waiter> waiters = (Queue<Waiter>) context.contextData().computeIfAbsent(this, ctx -> new ArrayDeque<>());

    NodeSelector selector = selectors.get(message.address());
    if (selector != null && waiters.isEmpty()) {
      context.runOnContext(v -> {
        handler.handle(Future.succeededFuture(selector.selectNode(message)));
      });
    } else {
      waiters.add(new Waiter(context, message, handler));
      if (waiters.size() == 1) {
        dequeueWaiters(context, waiters);
      }
    }
  }

  private void dequeueWaiters(ContextInternal context, Queue<Waiter> waiters) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }
    Waiter waiter;
    for (; ; ) {
      Waiter peeked = waiters.peek();
      if (peeked == null) {
        throw new IllegalStateException();
      }
      NodeSelector selector = selectors.get(peeked.message.address());
      if (selector != null) {
        Message<?> message = peeked.message;
        peeked.handleOnContext(Future.succeededFuture(selector.selectNode(message)));
        waiters.remove();
        if (waiters.isEmpty()) {
          return;
        }
      } else {
        waiter = peeked;
        break;
      }
    }

    clusterManager.registrationListener(waiter.message.address(), ar -> {
      if (ar.succeeded()) {
        registrationListenerCreated(context, waiters, ar.result());
      } else {
        waiter.handleOnContext(Future.failedFuture(ar.cause()));
        removeFirstAndDequeueWaiters(context, waiters);
      }
    });
  }

  private void registrationListenerCreated(ContextInternal context, Queue<Waiter> waiters, RegistrationStream registrationStream) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }

    Waiter waiter = waiters.peek();
    if (waiter == null) {
      throw new IllegalStateException();
    }

    if (registrationStream.initialState().isEmpty()) {
      waiter.handleOnContext(Future.succeededFuture(Collections.emptyList()));
      removeFirstAndDequeueWaiters(context, waiters);
      return;
    }

    NodeSelector candidate = new NodeSelector(selectors, registrationStream, nodeInfo);
    NodeSelector previous = selectors.putIfAbsent(waiter.message.address(), candidate);
    NodeSelector current = previous != null ? previous : candidate;

    waiter.handleOnContext(Future.succeededFuture(current.selectNode(waiter.message)));
    removeFirstAndDequeueWaiters(context, waiters);

    if (previous == null) {
      current.startListening();
    }
  }

  private void removeFirstAndDequeueWaiters(ContextInternal context, Queue<Waiter> waiters) {
    if (Vertx.currentContext() != context) {
      throw new IllegalStateException();
    }
    waiters.remove();
    if (!waiters.isEmpty()) {
      context.runOnContext(v -> {
        dequeueWaiters(context, waiters);
      });
    }
  }

  private static class Waiter {
    final ContextInternal context;
    final Message<?> message;
    final Handler<AsyncResult<List<NodeInfo>>> handler;

    Waiter(ContextInternal context, Message<?> message, Handler<AsyncResult<List<NodeInfo>>> handler) {
      this.context = context;
      this.message = message;
      this.handler = handler;
    }

    void handleOnContext(AsyncResult<List<NodeInfo>> asyncResult) {
      context.runOnContext(v -> handler.handle(asyncResult));
    }
  }

  private static class NodeSelector {
    final ConcurrentMap<String, NodeSelector> selectors;
    final RegistrationStream registrationStream;
    final NodeInfo nodeInfo;
    final List<NodeInfo> accessibleNodes;
    final AtomicInteger index = new AtomicInteger(0);

    NodeSelector(ConcurrentMap<String, NodeSelector> selectors, RegistrationStream registrationStream, NodeInfo nodeInfo) {
      this.selectors = selectors;
      this.registrationStream = registrationStream;
      this.nodeInfo = nodeInfo;
      accessibleNodes = compute(registrationStream.initialState());
    }

    NodeSelector(ConcurrentMap<String, NodeSelector> selectors, RegistrationStream registrationStream, NodeInfo nodeInfo, List<NodeInfo> accessibleNodes) {
      this.selectors = selectors;
      this.registrationStream = registrationStream;
      this.nodeInfo = nodeInfo;
      this.accessibleNodes = accessibleNodes;
    }

    List<NodeInfo> compute(List<RegistrationInfo> registrationInfos) {
      return registrationInfos.stream()
        .filter(this::isNodeAccessible)
        .map(RegistrationInfo::getNodeInfo)
        .collect(collectingAndThen(toCollection(ArrayList::new), Collections::unmodifiableList));
    }

    boolean isNodeAccessible(RegistrationInfo registrationInfo) {
      return !registrationInfo.isLocalOnly() || registrationInfo.getNodeInfo().equals(nodeInfo);
    }

    List<NodeInfo> selectNode(Message<?> message) {
      if (accessibleNodes.isEmpty()) {
        return Collections.emptyList();
      }
      if (message.isSend()) {
        return Collections.singletonList(accessibleNodes.get(index.incrementAndGet() % accessibleNodes.size()));
      }
      return accessibleNodes.stream()
        .distinct()
        .collect(collectingAndThen(toCollection(ArrayList::new), Collections::unmodifiableList));
    }

    void startListening() {
      registrationStream.exceptionHandler(t -> end()).endHandler(v -> end())
        .pause()
        .handler(this::registrationsUpdated)
        .fetch(1);
    }

    void registrationsUpdated(List<RegistrationInfo> registrationInfos) {
      if (!registrationInfos.isEmpty()) {
        NodeSelector newValue = new NodeSelector(selectors, registrationStream, nodeInfo, compute(registrationInfos));
        if (selectors.replace(registrationStream.address(), this, newValue)) {
          registrationStream.fetch(1);
          return;
        }
      }
      end();
    }

    void end() {
      registrationStream.close();
      selectors.remove(registrationStream.address());
    }
  }
}
