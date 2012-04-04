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
import org.vertx.java.core.impl.Context;
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

  private static final Logger log = LoggerFactory.getLogger(HandlerManager.class);

  private final VertxWorkerPool availableWorkers;
  private Map<NioWorker, Handlers> handlerMap = new ConcurrentHashMap<>();

  public HandlerManager(VertxWorkerPool availableWorkers) {
    this.availableWorkers = availableWorkers;
  }

  public synchronized boolean hasHandlers() {
    return availableWorkers.workerCount() > 0;
  }

  public synchronized HandlerHolder<T> chooseHandler(NioWorker worker) {
    Handlers handlers = handlerMap.get(worker);
    if (handlers == null) {
      return null;
    }
    return handlers.chooseHandler();
  }

  private NioWorker getWorker(Context context) {
    EventLoopContext ectx;
    if (context instanceof EventLoopContext) {
      //It always will be
      ectx = (EventLoopContext)context;
    } else {
      ectx = null;
    }
    NioWorker worker = ectx.getWorker();
    return worker;
  }

  public synchronized void addHandler(Handler<T> handler, Context context) {
    NioWorker worker = getWorker(context);
    availableWorkers.addWorker(worker);
    Handlers handlers = handlerMap.get(worker);
    if (handlers == null) {
      handlers = new Handlers();
      handlerMap.put(worker, handlers);
    }
    handlers.addHandler(new HandlerHolder<>(context, handler));
  }

  public synchronized void removeHandler(Handler<T> handler, Context context) {
    NioWorker worker = getWorker(context);
    Handlers handlers = handlerMap.get(worker);
    if (!handlers.removeHandler(new HandlerHolder<>(context, handler))) {
      throw new IllegalStateException("Can't find handler");
    }
    if (handlers.isEmpty()) {
      handlerMap.remove(worker);
    }
    //Available workers does it's own reference counting -since workers can be sharedd across different Handlers
    availableWorkers.removeWorker(worker);
  }

  private static class Handlers {
    int pos;
    final List<HandlerHolder> list = new ArrayList<>();
    HandlerHolder chooseHandler() {
      HandlerHolder handler = list.get(pos);
      pos++;
      checkPos();
      return handler;
    }

    void addHandler(HandlerHolder handler) {
      list.add(handler);
    }

    boolean removeHandler(HandlerHolder handler) {
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
