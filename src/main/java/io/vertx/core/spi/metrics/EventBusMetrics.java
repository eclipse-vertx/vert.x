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

package io.vertx.core.spi.metrics;

import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyFailure;

/**
 * The event bus metrics SPI which Vert.x will use to call when each event occurs.
 *
 * @author <a href="mailto:nscavell@redhat.com">Nick Scavelli</a>
 */
public interface EventBusMetrics<H> extends Metrics {

  /**
   * Called when a handler is registered on the event bus.<p/>
   * <p>
   * No specific thread and context can be expected when this method is called.
   *
   * @param address the address used to register the handler
   */
  default H handlerRegistered(String address) {
    return null;
  }

  /**
   * Called when a handler has been unregistered from the event bus.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param handler the unregistered handler
   */
  default void handlerUnregistered(H handler) {
  }

  /**
   * Schedule a message for processing.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param handler the handler processing the message
   * @param local when the scheduled message is local
   */
  default void scheduleMessage(H handler, boolean local) {
  }

  /**
   * Discard a message.
   * @param handler the handler processing the message
   * @param local when the scheduled message is local
   * @param msg the discarded message
   */
  default void discardMessage(H handler, boolean local, Message<?> msg) {
  }

  /**
   * Called when an handler has been delivered a message.
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param handler the handler processing the message
   * @param local when the handled message is local
   */
  default void messageDelivered(H handler, boolean local) {
  }

  /**
   * Called when a message has been sent or published.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param address the address
   * @param publish true when the message is published
   * @param local the message is processed locally
   * @param remote the message is sent on the cluster
   */
  default void messageSent(String address, boolean publish, boolean local, boolean remote) {
  }

  /**
   * Called when a message is received.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param address the address
   * @param publish true when the message is published
   * @param local true when the message is received locally
   * @param handlers the number of handlers that process the message
   */
  default void messageReceived(String address, boolean publish, boolean local, int handlers) {
  }

  /**
   * A message has been sent over the network.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param address the message address
   * @param numberOfBytes the number of bytes written
   */
  default void messageWritten(String address, int numberOfBytes) {
  }

  /**
   * A message has been received from the network.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param address the message address
   * @param numberOfBytes the number of bytes read
   */
  default void messageRead(String address, int numberOfBytes) {
  }

  /**
   * Called whenever the event bus delivers a reply failure to the sender/publisher, the
   * {@link io.vertx.core.eventbus.ReplyFailure reply failure} indicates the nature of the failure.<p/>
   *
   * No specific thread and context can be expected when this method is called.
   *
   * @param address the address
   * @param failure the {@link io.vertx.core.eventbus.ReplyFailure}
   */
  default void replyFailure(String address, ReplyFailure failure) {
  }
}
