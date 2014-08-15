/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.metrics;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import io.vertx.core.impl.VertxInternal;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public class EventBusMetrics extends AbstractMetrics {

  private Counter handlerCount;
  private Meter messages;
  private Meter receivedMessages;
  private Meter sentMessages;

  public EventBusMetrics(VertxInternal vertx) {
    super(vertx, "io.vertx.eventbus");
    if (isEnabled()) {
      this.handlerCount = counter("handlers");
      this.messages = meter("messages");
      this.receivedMessages = meter("messages-received");
      this.sentMessages = meter("messages-sent");
    }
  }

  public void register(String address) {
    if (!isEnabled()) return;

    handlerCount.inc();
    addressHandlerCounter(address).inc();
  }

  public void unregister(String address) {
    if (!isEnabled()) return;

    handlerCount.dec();
    addressHandlerCounter(address).dec();
  }

  public void sent(String address) {
    if (!isEnabled()) return;

    messages.mark();
    sentMessages.mark();
  }

  public void receive(String address) {
    if (!isEnabled()) return;

    messages.mark();
    receivedMessages.mark();
  }

  private Counter addressHandlerCounter(String address) {
    return counter("handlers", address);
  }
}
