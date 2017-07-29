package io.vertx.core.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

import static io.vertx.core.impl.ContextImpl.LONG_STACKS;

/**
 * @author Farid Zakaria
 */
class StackTracePropagationHandler {

  private final ContextImpl context;

  StackTracePropagationHandler(ContextImpl context) {
    this.context = context;
  }

  public <T> Handler<Future<T>> wrapWithFuture(Handler<Future<T>> delegate) {
    if (delegate == null) {
      return null;
    }

    //don't add extra long stack information if not enabled.
    if (!LONG_STACKS) {
      return delegate;
    }

    //Capture current stack
    final StackTraceElement[] currentStack = Thread.currentThread().getStackTrace();
    final Deque<StackTraceElement[]> threadStack = new ArrayDeque<>(context.asyncStack.get());
    threadStack.addFirst(currentStack);

    return (event) -> {
      //set thread local
      context.asyncStack.set(threadStack);

      try {
        delegate.handle(event);

        //Fix future if it failed
        if (event.failed()) {
          event.cause().setStackTrace(getAsyncStack(event.cause(), threadStack));
        }

      } catch (Throwable t) {
        t.setStackTrace(getAsyncStack(t, threadStack));
        throw t;
      } finally {
        threadStack.pollFirst();
      }
    };

  }

  public <T> Action<T> wrap(Action<T> delegate) {
    if (delegate == null) {
      return null;
    }

    //don't add extra long stack information if not enabled.
    if (!LONG_STACKS) {
      return delegate;
    }

    //Capture current stack
    final StackTraceElement[] currentStack = Thread.currentThread().getStackTrace();
    final Deque<StackTraceElement[]> threadStack = new ArrayDeque<>(context.asyncStack.get());
    threadStack.addFirst(currentStack);

    return () -> {
      //set thread local
      context.asyncStack.set(threadStack);

      try {
        return delegate.perform();
      } catch (Throwable t) {
        t.setStackTrace(getAsyncStack(t, threadStack));
        throw t;
      } finally {
        threadStack.pollFirst();
      }
    };
  }

  public Handler<Void> wrap(Handler<Void> delegate) {
    if (delegate == null) {
      return null;
    }

    //don't add extra long stack information if not enabled.
    if (!LONG_STACKS) {
      return delegate;
    }

    //Capture current stack
    final StackTraceElement[] currentStack = Thread.currentThread().getStackTrace();
    final Deque<StackTraceElement[]> threadStack = new ArrayDeque<>(context.asyncStack.get());
    threadStack.addFirst(currentStack);

    return (event) -> {
      //set thread local
      context.asyncStack.set(threadStack);

      try {
        delegate.handle(event);
      } catch (Throwable t) {
        t.setStackTrace(getAsyncStack(t, threadStack));
        throw t;
      } finally {
        threadStack.pollFirst();
      }
    };
  }

  private static StackTraceElement[] getAsyncStack(Throwable t, Deque<StackTraceElement[]> asyncStack) {
    int size = (int) asyncStack.stream().flatMap(Arrays::stream).count();
    StackTraceElement[] afterStack = t.getStackTrace();
    StackTraceElement[] newStack = new StackTraceElement[afterStack.length + size + asyncStack.size()];
    System.arraycopy(afterStack, 0, newStack, 0, afterStack.length);

    int index = afterStack.length;
    for (StackTraceElement[] stack : asyncStack) {
      newStack[index++] =
        new StackTraceElement("Async", "Async", null, 0);
      System.arraycopy(stack, 0, newStack, index, stack.length);
      index += stack.length;
    }
    return newStack;
  }

}
