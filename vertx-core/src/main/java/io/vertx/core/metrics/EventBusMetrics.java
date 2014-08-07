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

  //TODO: Do we want to provide metrics for every address ? i.e. inbound/outbound, handlers, etc. This could get overwhelming.
  private Counter handlerCount;
  private Meter messageMeter;
  private Meter inboundMessageMeter;
  private Meter outboundMessageMeter;

  public EventBusMetrics(VertxInternal vertx) {
    super(vertx, "io.vertx.eventbus");
    if (isEnabled()) {
      this.handlerCount = counter("handlers");
      this.messageMeter = meter("messages");
      this.inboundMessageMeter = meter("messages.inbound");
      this.outboundMessageMeter = meter("messages.outbound");
    }
  }

  public void register(String address) {
    if (!isEnabled()) return;

    handlerCount.inc();
//    addressHandlerCounter(address).inc();
  }

  public void unregister(String address) {
    if (!isEnabled()) return;

    handlerCount.dec();
//    addressHandlerCounter(address).dec();
  }

  public void send(String address) {
    if (!isEnabled()) return;

    markOutbound(address);
  }

  public void receive(String address) {
    if (!isEnabled()) return;

    markInbound(address);
  }

  private Counter addressHandlerCounter(String address) {
    return counter("handlers", address);
  }

  private void markInbound(String address) {
    markMessage(address);

    inboundMessageMeter.mark();
//    meter("messages", "inbound", address).mark();
  }

  private void markOutbound(String address) {
    markMessage(address);

    outboundMessageMeter.mark();
//    meter("messages", "outbound", address).mark();
  }

  private void markMessage(String address) {
    messageMeter.mark();
//    meter("messages", address).mark();
  }
}
