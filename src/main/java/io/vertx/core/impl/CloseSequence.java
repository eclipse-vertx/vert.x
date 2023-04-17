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
package io.vertx.core.impl;

import io.vertx.core.Closeable;
import io.vertx.core.Future;
import io.vertx.core.Promise;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-steps sequence for closing a resource.
 *
 * <p> The sequence index is initialized with the number of steps of the sequence. The value can then be decreased by any amount
 * between 1 and the number of remaining steps to 0. When the index reaches 0, the sequence is ended.
 *
 * <p> It is up to the sequence owner to define the steps of the sequence and what each step performs.
 *
 * <p> A sequence can be added to a close future hook, when the sequence completes it is removed from its owning future.
 */
public class CloseSequence extends NestedCloseable implements Closeable {

  private int current;
  private int idx;
  private final Closeable[] sequence;
  private final List<Promise<Void>> steps;

  public CloseSequence(Closeable... sequence) {
    if (sequence.length == 0) {
      throw new IllegalArgumentException();
    }
    List<Promise<Void>> steps = new ArrayList<>(sequence.length);
    for (int i = 0; i < sequence.length; i++) {
      steps.add(Promise.promise());
    }
    this.sequence = sequence;
    this.steps = steps;
    this.current = sequence.length;
    this.idx = sequence.length;
  }

  public synchronized boolean started() {
    return idx < sequence.length;
  }

  /**
   * Advance the sequence to the specified {@code step}.
   *
   * @param step the target step
   * @return the future completed upon step completion
   * @throws IllegalArgumentException when the {@code step} falls out of the actual range
   */
  public Future<Void> progressTo(int step) {
    if (step < 0 || step > steps.size() - 1) {
      throw new IllegalArgumentException("Invalid step");
    }
    boolean checkProgress;
    synchronized (this) {
      if (step < idx) {
        checkProgress = idx == current;
        idx = step;
      } else {
        checkProgress = false;
      }
    }
    if (checkProgress) {
      tryProgress();
    }
    return steps.get(step).future();
  }

  private void tryProgress() {
    int curr;
    synchronized (this) {
      curr = current;
    }
    Promise<Void> promise = steps.get(curr - 1);
    sequence[curr - 1].close(promise);
    promise.future().onComplete(ar -> {
      boolean progress;
      CloseFuture owner;
      synchronized (CloseSequence.this) {
        current = curr - 1;
        if (idx == current) {
          if (idx == 0) {
            owner = super.owner;
          } else {
            owner = null;
          }
          progress = false;
        } else {
          progress = true;
          owner = null;
        }
      }
      if (progress) {
        tryProgress();
      } else if (owner != null) {
        owner.remove(this);
      }
    });
  }

  /**
   * @return the future completed upon sequence completion
   */
  public Future<Void> future() {
    return steps.get(0).future();
  }

  /**
   * Execute all the steps of the sequence.
   *
   * @return the future completed upon completion of the last step of the sequence
   */
  public Future<Void> close() {
    return progressTo(0);
  }

  @Override
  public void close(Promise<Void> completion) {
    progressTo(0).onComplete(completion);
  }
}
