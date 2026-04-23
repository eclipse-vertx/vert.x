/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

/**
 * Options configuring the behavior of a event-bus message consumer.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
@JsonGen(publicConverter = false)
public class MessageConsumerOptions {

  /**
   * The default number of max buffered messages = {@code 1000}
   */
  public static final int DEFAULT_MAX_BUFFERED_MESSAGES = 1000;

  /**
   * The default consumer locality = {@code false}
   */
  public static final boolean DEFAULT_LOCAL_ONLY = false;

  /**
   * The default auto-ack mode = {@code true}
   */
  public static final boolean DEFAULT_AUTO_ACK = true;

  private String address;
  private boolean localOnly;
  private int maxBufferedMessages;
  private boolean autoAck;

  /**
   * Default constructor
   */
  public MessageConsumerOptions() {
    maxBufferedMessages = DEFAULT_MAX_BUFFERED_MESSAGES;
    localOnly = DEFAULT_LOCAL_ONLY;
    autoAck = DEFAULT_AUTO_ACK;
  }

  /**
   * Copy constructor
   *
   * @param other The other {@code VertxOptions} to copy when creating this
   */
  public MessageConsumerOptions(MessageConsumerOptions other) {
    this();
    maxBufferedMessages = other.getMaxBufferedMessages();
    localOnly = other.isLocalOnly();
    address = other.getAddress();
    autoAck = other.isAutoAck();
  }

  /**
   * Create an instance from a {@link io.vertx.core.json.JsonObject}
   *
   * @param json the JsonObject to create it from
   */
  public MessageConsumerOptions(JsonObject json) {
    this();
    MessageConsumerOptionsConverter.fromJson(json, this);
  }

  /**
   * @return  the address the event-bus will register the consumer at
   */
  public String getAddress() {
    return address;
  }

  /**
   * Set the address the event-bus will register the consumer at.
   *
   * @param address the consumer address
   * @return this options
   */
  public MessageConsumerOptions setAddress(String address) {
    this.address = address;
    return this;
  }

  /**
   * @return whether the consumer is local only
   */
  public boolean isLocalOnly() {
    return localOnly;
  }

  /**
   * Set whether the consumer is local only.
   *
   * @param localOnly whether the consumer is local only
   * @return this options
   */
  public MessageConsumerOptions setLocalOnly(boolean localOnly) {
    this.localOnly = localOnly;
    return this;
  }

  /**
   * @return the maximum number of messages that can be buffered when this stream is paused
   */
  public int getMaxBufferedMessages() {
    return maxBufferedMessages;
  }

  /**
   * Set the number of messages this registration will buffer when this stream is paused. The default
   * value is <code>1000</code>.
   * <p>
   * When a new value is set, buffered messages may be discarded to reach the new value. The most recent
   * messages will be kept.
   *
   * @param maxBufferedMessages the maximum number of messages that can be buffered
   * @return this options
   */
  public MessageConsumerOptions setMaxBufferedMessages(int maxBufferedMessages) {
    Arguments.require(maxBufferedMessages >= 0, "Max buffered messages cannot be negative");
    this.maxBufferedMessages = maxBufferedMessages;
    return this;
  }

  /**
   * @return whether message ack is signaled automatically
   */
  public boolean isAutoAck() {
    return autoAck;
  }

  /**
   * Set whether message ack should be signaled automatically.
   * <p>
   * When {@code true} (default), ack is signaled automatically when the message handler returns.
   * <p>
   * When {@code false}, the user must explicitly call {@link Message#ack()} to ack the message.
   * This is useful for async/blocking handlers where you need precise control over when tracing
   * spans are closed.
   * <p>
   * Note: For request/reply messages, calling {@link Message#reply(Object)} always acks the
   * message regardless of this setting.
   *
   * @param autoAck whether to automatically ack messages
   * @return this options instance for chaining
   */
  public MessageConsumerOptions setAutoAck(boolean autoAck) {
    this.autoAck = autoAck;
    return this;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    MessageConsumerOptionsConverter.toJson(this, json);
    return json;
  }
}
