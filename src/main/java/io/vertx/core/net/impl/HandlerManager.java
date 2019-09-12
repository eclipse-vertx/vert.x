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

package io.vertx.core.net.impl;

import io.netty.channel.EventLoop;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerManager<T> {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(HandlerManager.class);

  private final VertxEventLoopGroup availableWorkers;
  private final ConcurrentMap<EventLoop, Handlers<T>> handlerMap = new ConcurrentHashMap<>();

  // We maintain a separate hasHandlers variable so we can implement hasHandlers() efficiently
  // As it is called for every HTTP message received
  private volatile boolean hasHandlers;

  public HandlerManager(VertxEventLoopGroup availableWorkers) {
    this.availableWorkers = availableWorkers;
  }

  public boolean hasHandlers() {
    return hasHandlers;
  }

  public synchronized List<T> handlers() {
    return handlerMap.values().stream()
      .flatMap(handlers -> handlers.list.stream())
      .map(holder -> holder.handler)
      .collect(Collectors.toList());
  }

  public HandlerHolder<T> chooseHandler(EventLoop worker) {
    Handlers<T> handlers = handlerMap.get(worker);
    return handlers == null ? null : handlers.chooseHandler();
  }

  public synchronized void addHandler(T handler, ContextInternal context) {
    EventLoop worker = context.nettyEventLoop();
    availableWorkers.addWorker(worker);
    Handlers<T> handlers = new Handlers<>();
    Handlers<T> prev = handlerMap.putIfAbsent(worker, handlers);
    if (prev != null) {
      handlers = prev;
    }
    handlers.addHandler(new HandlerHolder<>(context, handler));
    hasHandlers = true;
  }

  public synchronized void removeHandler(T handler, ContextInternal context) {
    EventLoop worker = context.nettyEventLoop();
    Handlers<T> handlers = handlerMap.get(worker);
    if (!handlers.removeHandler(new HandlerHolder<>(context, handler))) {
      throw new IllegalStateException("Can't find handler");
    }
    if (handlers.isEmpty()) {
      handlerMap.remove(worker);
    }
    if (handlerMap.isEmpty()) {
      hasHandlers = false;
    }
    //Available workers does it's own reference counting -since workers can be shared across different Handlers
    availableWorkers.removeWorker(worker);
  }

  private static final class Handlers<T> {
    private int pos;
    private final List<HandlerHolder<T>> list = new CopyOnWriteArrayList<>();
    HandlerHolder<T> chooseHandler() {
      HandlerHolder<T> handler = list.get(pos);
      pos++;
      checkPos();
      return handler;
    }

    void addHandler(HandlerHolder<T> handler) {
      list.add(handler);
    }

    boolean removeHandler(HandlerHolder<T> handler) {
      if (list.remove(handler)) {
        checkPos();
        return true;
      } else {
        return false;
      }
    }

    boolean isEmpty() {
      return list.isEmpty();
    }

    void checkPos() {
      if (pos == list.size()) {
        pos = 0;
      }
    }
  }

}
