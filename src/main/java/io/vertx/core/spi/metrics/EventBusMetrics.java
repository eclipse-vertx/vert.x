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

package io.vertx.core.spi.metrics;

import io.vertx.core.eventbus.ReplyFailure;

/**
 * The event bus metrics SPI which Vert.x will use to call when each event occurs.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface EventBusMetrics extends BaseMetrics {

  /**
   * Called when a handler is registered on the event bus.
   *
   * @param address the address used to register the handler
   */
  void handlerRegistered(String address);

  /**
   * Called when a handler has been unregistered from the event bus.
   *
   * @param address the address that was used to register the handler
   */
  void handlerUnregistered(String address);

  /**
   * Called when a message has been sent or published.
   *
   * @param address the address
   * @param publish true if it was a publish
   */
  void messageSent(String address, boolean publish);

  /**
   * Called when a message is received
   *
   * @param address the address
   */
  void messageReceived(String address);

  /**
   * Called whenever there is a reply failure on the event bus
   *
   * @param address the address
   * @param failure the {@link io.vertx.core.eventbus.ReplyFailure}
   */
  void replyFailure(String address, ReplyFailure failure);
}
