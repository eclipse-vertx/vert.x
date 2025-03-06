/*
 * Copyright (c) 2011-2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core;

/**
 * The threading model defines the scheduler to execute context tasks.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum ThreadingModel {

  /**
   * Tasks are scheduled on the event-loop thread of the vertx instance.
   */
  EVENT_LOOP,

  /**
   * Tasks are scheduled on a worker pool of platform threads managed by the vertx instance.
   */
  WORKER,

  /**
   * Tasks are scheduled on a virtual thread, no assumption on whether virtual threads are pooled.
   */
  VIRTUAL_THREAD,

  /**
   * Tasks are scheduled on a virtual thread mounted on a Vert.x event-loop thread
   */
  VIRTUAL_THREAD_MOUNTED_ON_EVENT_LOOP,

  /**
   * Tasks are scheduled on threads not managed by the current vertx instance, the nature of the thread is unknown
   * to the vertx instance. Note that an event-loop thread of another vertx instance falls in this category.
   */
  OTHER
}

