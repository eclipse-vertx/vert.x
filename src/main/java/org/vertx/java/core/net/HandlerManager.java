package org.vertx.java.core.net;

import org.jboss.netty.channel.socket.nio.NioWorker;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxInternal;
import org.vertx.java.core.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HandlerManager<T> {

  private static final Logger log = Logger.getLogger(HandlerManager.class);

  private final NetServerWorkerPool availableWorkers;
  private Map<NioWorker, Handlers> handlerMap = new ConcurrentHashMap<>();

  public HandlerManager(NetServerWorkerPool availableWorkers) {
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

  public synchronized void addHandler(Handler<T> handler) {
    long contextID = Vertx.instance.getContextID();
    NioWorker worker = VertxInternal.instance.getWorkerForContextID(contextID);
    availableWorkers.addWorker(worker);
    Handlers handlers = handlerMap.get(worker);
    if (handlers == null) {
      handlers = new Handlers();
      handlerMap.put(worker, handlers);
    }
    handlers.addHandler(new HandlerHolder<>(contextID, handler));
  }

  public synchronized void removeHandler(Handler<T> handler) {
    long contextID = Vertx.instance.getContextID();
    NioWorker worker = VertxInternal.instance.getWorkerForContextID(contextID);
    Handlers handlers = handlerMap.get(worker);
    if (!handlers.removeHandler(new HandlerHolder<>(contextID, handler))) {
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
