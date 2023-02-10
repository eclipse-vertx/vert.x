package io.vertx.core.net.impl;

import java.util.ArrayDeque;
import java.util.Deque;

public class MessageQueue {

  private Deque<Object> pending;
  boolean paused;
  Object processing;
  boolean handling;

  public final boolean read(Object msg) {
    if (processing != null) {
      if (pending == null) {
        pending = new ArrayDeque<>();
      }
      pending.add(msg);
      paused |= pending.size() >= 16;
    } else {
      processing = msg;
      checkLoop();
    }
    return !paused;
  }

  private void checkLoop() {
    if (handling) {
      return;
    }
    handling = true;
    try {
      while (true) {
        Object current = processing;
        if (current != null) {
          handleMessage(current);
          if (current == processing) {
            break;
          }
        } else if (paused) {
          paused = false;
          handleDrain();
        } else {
          break;
        }
      }
    } finally {
      handling = false;
    }
  }

  public final void ack(Object msg) {
    // Must be executed from EL thread
    if (processing != msg) {
      throw new IllegalStateException();
    }
    if (pending != null) {
      processing = pending.poll();
    } else {
      processing = null;
    }
    checkLoop();
  }

  public void handleDrain() {

  }

  public void handleMessage(Object msg) {
    ack(msg);
  }
}
