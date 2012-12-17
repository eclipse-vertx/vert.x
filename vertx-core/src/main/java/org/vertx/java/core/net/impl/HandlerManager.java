/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.core.net.impl;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.EventLoopContext;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerManager<T> {

  @SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(HandlerManager.class);

  private final VertxWorkerPool availableWorkers;
  private Map<NioWorker, Handlers<T>> handlerMap = new ConcurrentHashMap<>();

  public HandlerManager(VertxWorkerPool availableWorkers) {
    this.availableWorkers = availableWorkers;
  }

  public synchronized boolean hasHandlers() {
    return availableWorkers.workerCount() > 0;
  }

  public synchronized HandlerHolder<T> chooseHandler(NioWorker worker) {
    Handlers<T> handlers = handlerMap.get(worker);
    if (handlers == null) {
      return null;
    }
    return handlers.chooseHandler();
  }

  public synchronized void addHandler(Handler<T> handler, EventLoopContext context) {
    NioWorker worker = context.getWorker();
    availableWorkers.addWorker(worker);
    Handlers<T> handlers = handlerMap.get(worker);
    if (handlers == null) {
      handlers = new Handlers<>();
      handlerMap.put(worker, handlers);
    }
    handlers.addHandler(new HandlerHolder<>(context, handler));
  }

  public synchronized void removeHandler(Handler<T> handler, EventLoopContext context) {
    NioWorker worker = context.getWorker();
    Handlers<T> handlers = handlerMap.get(worker);
    if (!handlers.removeHandler(new HandlerHolder<>(context, handler))) {
      throw new IllegalStateException("Can't find handler");
    }
    if (handlers.isEmpty()) {
      handlerMap.remove(worker);
    }
    //Available workers does it's own reference counting -since workers can be shared across different Handlers
    availableWorkers.removeWorker(worker);
  }

  private static class Handlers<T> {
    int pos;
    final List<HandlerHolder<T>> list = new ArrayList<>();
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
