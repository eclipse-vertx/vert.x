/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net.impl.pool;

public abstract class Task {

  private Task next;

  public Task next(Task next) {
    this.next = next;
    return next;
  }

  public void runNextTasks() {
    Task task = this;
    while (task != null) {
      task.run();
      final Task next = task.next;
      // help GC :P
      task.next = null;
      task = next;
    }
  }

  public abstract void run();
}
