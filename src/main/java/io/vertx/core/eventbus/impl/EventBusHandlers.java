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

package io.vertx.core.eventbus.impl;

import io.vertx.core.impl.utils.ConcurrentCyclicSequence;

import java.util.Iterator;

@SuppressWarnings("rawtypes")
public class EventBusHandlers implements Handlers {

  private final ConcurrentCyclicSequence<HandlerHolder> sequence;

  public EventBusHandlers() {
    sequence = new ConcurrentCyclicSequence<>();
  }

  private EventBusHandlers(ConcurrentCyclicSequence<HandlerHolder> sequence) {
    this.sequence = sequence;
  }

  @Override
  public Handlers add(HandlerHolder holder) {
    return new EventBusHandlers(sequence.add(holder));
  }

  @Override
  public Handlers remove(HandlerHolder holder) {
    return new EventBusHandlers(sequence.remove(holder));
  }

  @Override
  public boolean isEmpty() {
    return sequence.size() == 0;
  }

  @Override
  public HandlerHolder next(boolean includeLocalOnly) {
    return sequence.next();
  }

  @Override
  public int count(boolean includeLocalOnly) {
    return sequence.size();
  }

  @Override
  public Iterator<HandlerHolder> iterator(boolean includeLocalOnly) {
    return sequence.iterator();
  }
}
