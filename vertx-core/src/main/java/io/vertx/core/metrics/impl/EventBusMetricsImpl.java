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

package io.vertx.core.metrics.impl;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Meter;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.metrics.spi.EventBusMetrics;

/**
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
class EventBusMetricsImpl extends AbstractMetrics implements EventBusMetrics {

  private Counter handlerCount;
  private Meter receivedMessages;
  private Meter sentMessages;
  private Meter publishedMessages;
  private Meter replyFailures;

  public EventBusMetricsImpl(AbstractMetrics metrics, String baseName) {
    super(metrics.registry(), baseName);
    initialize();
  }

  private void initialize() {
    if (!isEnabled()) return;

    this.handlerCount = counter("handlers");
    this.receivedMessages = meter("messages", "received");
    this.sentMessages = meter("messages", "sent");
    this.publishedMessages = meter("messages", "published");
    this.replyFailures = meter("messages", "reply-failures");
  }

  @Override
  public void handlerRegistered(String address) {
    if (!isEnabled()) return;

    handlerCount.inc();
  }

  @Override
  public void handlerUnregistered(String address) {
    if (!isEnabled()) return;

    handlerCount.dec();
  }

  @Override
  public void messageSent(String address, boolean publish) {
    if (!isEnabled()) return;

    if (publish) {
      publishedMessages.mark();
    } else {
      sentMessages.mark();
    }
  }

  @Override
  public void messageReceived(String address) {
    if (!isEnabled()) return;

    receivedMessages.mark();
  }

  @Override
  public void replyFailure(String address, ReplyFailure failure) {
    if (!isEnabled()) return;

    replyFailures.mark();
    meter("messages", "reply-failures", failure.name()).mark();
  }
}
