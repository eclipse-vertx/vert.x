package io.vertx.core.eventbus;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.json.annotations.JsonGen;
import io.vertx.core.impl.Arguments;
import io.vertx.core.json.JsonObject;

@DataObject
@JsonGen(publicConverter = false)
public class MessageConsumerOptions {

  public static final int DEFAULT_MAX_BUFFERED_MESSAGES = 1000;
  public static final boolean DEFAULT_LOCAL_ONLY = false;

  private String address;
  private boolean localOnly;
  private int maxBufferedMessages;

  public MessageConsumerOptions() {
    maxBufferedMessages = DEFAULT_MAX_BUFFERED_MESSAGES;
    localOnly = DEFAULT_LOCAL_ONLY;
  }

  public MessageConsumerOptions(MessageConsumerOptions options) {
    this();
    maxBufferedMessages = options.getMaxBufferedMessages();
    localOnly = options.isLocalOnly();
    address = options.getAddress();
  }

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

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    MessageConsumerOptionsConverter.toJson(this, json);
    return json;
  }
}
