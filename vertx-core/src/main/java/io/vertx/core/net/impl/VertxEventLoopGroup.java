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

package io.vertx.core.net.impl;

import io.netty.channel.*;
import io.netty.util.concurrent.*;
import io.vertx.core.Handler;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@SuppressWarnings("deprecation")
public final class VertxEventLoopGroup extends AbstractEventExecutorGroup implements EventLoopGroup {

  private final AtomicReference<EventLoopLoadBalancer> eventLoopLoadBalancerRef;

  public VertxEventLoopGroup() {
    eventLoopLoadBalancerRef = new AtomicReference<>(new EventLoopLoadBalancer(0, List.of()));
  }

  @Override
  public EventLoop next() {
    EventLoopLoadBalancer eventLoopLoadBalancer = eventLoopLoadBalancerRef.get();
    EventLoop eventLoop = eventLoopLoadBalancer.selectEventLoop();
    if (eventLoop == null) {
      throw new IllegalStateException();
    } else {
      return eventLoop;
    }
  }

  @Override
  public Iterator<EventExecutor> iterator() {
    return new EventLoopIterator(eventLoopLoadBalancerRef.get().handlerLoadBalancers.iterator());
  }

  @Override
  public ChannelFuture register(Channel channel) {
    return next().register(channel);
  }

  @Override
  public ChannelFuture register(Channel channel, ChannelPromise promise) {
    return next().register(channel, promise);
  }

  @Override
  public ChannelFuture register(ChannelPromise promise) {
    return next().register(promise);
  }

  @Override
  public boolean isShutdown() {
    return false;
  }

  @Override
  public boolean isTerminated() {
    return isShutdown();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) {
    return false;
  }

  public void shutdown() {
    throw new UnsupportedOperationException("Should never be called");
  }

  @Override
  public boolean isShuttingDown() {
    return false;
  }

  @Override
  public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
    throw new UnsupportedOperationException("Should never be called");
  }

  @Override
  public Future<?> terminationFuture() {
    throw new UnsupportedOperationException("Should never be called");
  }

  public void addHandler(EventLoop eventLoop, Handler<Channel> handler) {
    while (true) {
      EventLoopLoadBalancer prev = eventLoopLoadBalancerRef.get();
      EventLoopLoadBalancer next = prev.addHandler(eventLoop, handler);
      if (eventLoopLoadBalancerRef.compareAndSet(prev, next)) {
        break;
      }
    }
  }

  public boolean removeHandler(EventLoop eventLoop, Handler<Channel> handler) {
    while (true) {
      EventLoopLoadBalancer prev = eventLoopLoadBalancerRef.get();
      EventLoopLoadBalancer next = prev.removeHandler(eventLoop, handler);
      if (eventLoopLoadBalancerRef.compareAndSet(prev, next)) {
        return !next.handlerLoadBalancers.isEmpty();
      }
    }
  }

  public Handler<Channel> chooseHandler(EventLoop eventLoop) {
    EventLoopLoadBalancer current = eventLoopLoadBalancerRef.get();
    int idx = indexOfEventLoop(current.handlerLoadBalancers, eventLoop);
    if (idx == -1) {
      return null;
    } else {
      return current.handlerLoadBalancers.get(idx)
        .chooseHandler();
    }
  }

  private static class EventLoopLoadBalancer extends AtomicInteger {

    private final List<HandlerLoadBalancer> handlerLoadBalancers;

    public EventLoopLoadBalancer(int pos, List<HandlerLoadBalancer> handlerLoadBalancers) {
      super(pos);
      this.handlerLoadBalancers = handlerLoadBalancers;
    }

    public EventLoop selectEventLoop() {
      if (handlerLoadBalancers.isEmpty()) {
        return null;
      } else {
        int idx = getAndIncrement();
        if (idx >= handlerLoadBalancers.size()) {
          // Racy but ok
          idx = 0;
          set(1);
        }
        return handlerLoadBalancers.get(idx).eventLoop;
      }
    }

    public EventLoopLoadBalancer addHandler(EventLoop eventLoop, Handler<Channel> handler) {
      List<HandlerLoadBalancer> copyOfHandlerLoadBalancers = new ArrayList<>(handlerLoadBalancers);
      int idx = indexOfEventLoop(copyOfHandlerLoadBalancers, eventLoop);
      if (idx == -1) {
        copyOfHandlerLoadBalancers.add(new HandlerLoadBalancer(0, eventLoop, List.of(handler)));
      } else {
        copyOfHandlerLoadBalancers.set(idx, copyOfHandlerLoadBalancers.get(idx).addHandler(handler));
      }
      return new EventLoopLoadBalancer(get(), copyOfHandlerLoadBalancers);
    }


    public EventLoopLoadBalancer removeHandler(EventLoop eventLoop, Handler<Channel> handler) {
      int idx = indexOfEventLoop(handlerLoadBalancers, eventLoop);
      if (idx == -1) {
        throw new IllegalStateException("Can't find event-loop to remove");
      }
      HandlerLoadBalancer copyOfHandlerLoadBalancer = handlerLoadBalancers.get(idx)
        .removeHandler(handler);
      List<HandlerLoadBalancer> copyOfHandlerLoadBalancers = new ArrayList<>(handlerLoadBalancers);
      if (copyOfHandlerLoadBalancer.handlers.isEmpty()) {
        copyOfHandlerLoadBalancers.remove(idx);
      } else {
        copyOfHandlerLoadBalancers.set(idx, copyOfHandlerLoadBalancer);
      }
      return new EventLoopLoadBalancer(get(), copyOfHandlerLoadBalancers);
    }
  }

  private static class HandlerLoadBalancer extends AtomicInteger {

    private final EventLoop eventLoop;
    private final List<Handler<Channel>> handlers;

    HandlerLoadBalancer(int pos, EventLoop eventLoop, List<Handler<Channel>> handlers) {
      super(pos);
      this.eventLoop = eventLoop;
      this.handlers = handlers;
    }

    Handler<Channel> chooseHandler() {
      int idx = getAndIncrement();
      if (idx >= handlers.size()) {
        // Racy but ok
        idx = 0;
        set(1);
      }
      return handlers.get(idx);
    }

    public HandlerLoadBalancer removeHandler(Handler<Channel> handler) {
      List<Handler<Channel>> handlersCopy = new ArrayList<>(handlers);
      if (!handlersCopy.remove(handler)) {
        throw new IllegalStateException("Can't find handler to remove");
      }
      return new HandlerLoadBalancer(get(), eventLoop, handlersCopy);
    }

    public HandlerLoadBalancer addHandler(Handler<Channel> handler) {
      List<Handler<Channel>> copyOfHandlers = new ArrayList<>(handlers.size() + 1);
      copyOfHandlers.addAll(handlers);
      copyOfHandlers.add(handler);
      return new HandlerLoadBalancer(get(), eventLoop, copyOfHandlers);
    }
  }

  private static int indexOfEventLoop(List<HandlerLoadBalancer> handlersLoadBalancers, EventLoop eventLoop) {
    int count = 0;
    for (HandlerLoadBalancer handlerLoadBalancer : handlersLoadBalancers) {
      if (handlerLoadBalancer.eventLoop == eventLoop) {
        return count;
      }
      count++;
    }
    return -1;
  }

  private static final class EventLoopIterator implements Iterator<EventExecutor> {

    private final Iterator<HandlerLoadBalancer> holderIt;

    public EventLoopIterator(Iterator<HandlerLoadBalancer> holderIt) {
      this.holderIt = holderIt;
    }

    @Override
    public boolean hasNext() {
      return holderIt.hasNext();
    }

    @Override
    public EventExecutor next() {
      return holderIt.next().eventLoop;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException("read-only");
    }
  }
}
