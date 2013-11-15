/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.core.net.impl;

import io.netty.channel.EventLoop;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.DefaultContext;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerManager<T> {

  @SuppressWarnings("unused")
  private static final Logger log = LoggerFactory.getLogger(HandlerManager.class);

  private final VertxEventLoopGroup availableWorkers;
  private final Map<EventLoop, Handlers<T>> handlerMap = new HashMap<>();

  public HandlerManager(VertxEventLoopGroup availableWorkers) {
    this.availableWorkers = availableWorkers;
  }

  public synchronized boolean hasHandlers() {
    return !handlerMap.isEmpty();
  }

  public synchronized HandlerHolder<T> chooseHandler(EventLoop worker) {
    Handlers<T> handlers = handlerMap.get(worker);
    if (handlers == null) {
      return null;
    }
    return handlers.chooseHandler();
  }

  public void addHandler(Handler<T> handler, DefaultContext context) {
    EventLoop worker = context.getEventLoop();
    availableWorkers.addWorker(worker);
    synchronized (this) {
      Handlers<T> handlers = handlerMap.get(worker);
      if (handlers == null) {
        handlers = new Handlers<>();
        handlerMap.put(worker, handlers);
      }
      handlers.addHandler(new HandlerHolder<>(context, handler));
    }
  }

  public void removeHandler(Handler<T> handler, DefaultContext context) {
    EventLoop worker = context.getEventLoop();
    synchronized (this) {
      Handlers<T> handlers = handlerMap.get(worker);
      if (!handlers.removeHandler(new HandlerHolder<>(context, handler))) {
        throw new IllegalStateException("Can't find handler");
      }
      if (handlers.isEmpty()) {
        handlerMap.remove(worker);
      }
    }
    //Available workers does it's own reference counting -since workers can be shared across different Handlers
    availableWorkers.removeWorker(worker);
  }

  private static class Handlers<T> {
    private int pos;
    private final List<HandlerHolder<T>> list = new ArrayList<>();
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
