/*
 * Copyright 2018 Red Hat, Inc. and others
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.core.instrumentation;

import io.vertx.core.Handler;
import io.vertx.core.spi.instrumentation.Instrumentation;
import org.junit.Assert;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class TestInstrumentation implements Instrumentation {

  private final ThreadLocal<Deque<ContinuationImpl>> currentStack = ThreadLocal.withInitial(ArrayDeque::new);
  private final List<AssertionError> errs = new ArrayList<>();

  private class ContinuationHandler<T> implements Handler<T> {

    private final ContinuationImpl cont;
    private final Handler<T> handler;

    private ContinuationHandler(ContinuationImpl cont, Handler<T> handler) {
      this.cont = cont;
      this.handler = handler;
    }

    @Override
    public void handle(T event) {
      cont.resume();
      try {
        handler.handle(event);
      } finally {
        cont.suspend();
      }
    }
  }

  @Override
  public <T> Handler<T> unwrapContinuation(Handler<T> wrapper) {
    return ((ContinuationHandler) wrapper).handler;
  }

  @Override
  public <T> Handler<T> captureContinuation(Handler<T> handler) {
    ContinuationImpl cont = currentStack.get().peekLast();
    if (cont != null) {
      if (handler instanceof ContinuationHandler) {
        ContinuationHandler continuationHandler = (ContinuationHandler) handler;
        if (cont == continuationHandler.cont) {
          return handler;
        } else {
          throw new AssertionError("What should we do ?");
        }
      } else {
        return new ContinuationHandler<>(cont, handler);
      }
    } else {
      return handler;
    }
  }

  private class ContinuationImpl implements TestContinuation {

    public void resume() {
      currentStack.get().addLast(this);
    }

    public void suspend() {
      try {
        Assert.assertSame(this, currentStack.get().removeLast());
      } catch (AssertionError t) {
        errs.add(t);
      }
    }
  }

  public void assertNoErrors() {
    errs.forEach(err -> {
      throw err;
    });
  }

  public TestContinuation continuation() {
    return new ContinuationImpl();
  }

  public TestContinuation current() {
    return currentStack.get().peekLast();
  }

}
