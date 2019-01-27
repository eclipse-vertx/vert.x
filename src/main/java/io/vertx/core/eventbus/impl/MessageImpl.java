/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.eventbus.impl;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.*;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class MessageImpl<U, V> implements Message<V> {

  private static final Logger log = LoggerFactory.getLogger(MessageImpl.class);

  protected MessageCodec<U, V> messageCodec;
  protected final EventBusImpl bus;
  protected String address;
  protected String replyAddress;
  protected MultiMap headers;
  protected U sentBody;
  protected V receivedBody;
  protected boolean send;

  public MessageImpl(EventBusImpl bus) {
    this.bus = bus;
  }

  public MessageImpl(String address, String replyAddress, MultiMap headers, U sentBody,
                     MessageCodec<U, V> messageCodec,
                     boolean send, EventBusImpl bus) {
    this.messageCodec = messageCodec;
    this.address = address;
    this.replyAddress = replyAddress;
    this.headers = headers;
    this.sentBody = sentBody;
    this.send = send;
    this.bus = bus;
  }

  protected MessageImpl(MessageImpl<U, V> other) {
    this.bus = other.bus;
    this.address = other.address;
    this.replyAddress = other.replyAddress;
    this.messageCodec = other.messageCodec;
    if (other.headers != null) {
      List<Map.Entry<String, String>> entries = other.headers.entries();
      this.headers = new CaseInsensitiveHeaders();
      for (Map.Entry<String, String> entry: entries) {
        this.headers.add(entry.getKey(), entry.getValue());
      }
    }
    if (other.sentBody != null) {
      this.sentBody = other.sentBody;
      this.receivedBody = messageCodec.transform(other.sentBody);
    }
    this.send = other.send;
  }

  public MessageImpl<U, V> copyBeforeReceive() {
    return new MessageImpl<>(this);
  }

  @Override
  public String address() {
    return address;
  }

  @Override
  public MultiMap headers() {
    // Lazily decode headers
    if (headers == null) {
      headers = new CaseInsensitiveHeaders();
    }
    return headers;
  }

  @Override
  public V body() {
    if (receivedBody == null && sentBody != null) {
      receivedBody = messageCodec.transform(sentBody);
    }
    return receivedBody;
  }

  @Override
  public String replyAddress() {
    return replyAddress;
  }

  @Override
  public void fail(int failureCode, String message) {
    reply(new ReplyException(ReplyFailure.RECIPIENT_FAILURE, failureCode, message));
  }

  @Override
  public void reply(Object message) {
    reply(message, new DeliveryOptions(), null);
  }

  @Override
  public <R> void reply(Object message, Handler<AsyncResult<Message<R>>> replyHandler) {
    reply(message, new DeliveryOptions(), replyHandler);
  }

  @Override
  public void reply(Object message, DeliveryOptions options) {
    reply(message, options, null);
  }

  @Override
  public <R> void reply(Object message, DeliveryOptions options, Handler<AsyncResult<Message<R>>> replyHandler) {
    if (replyAddress != null) {
      MessageImpl reply = bus.createMessage(true, replyAddress, options.getHeaders(), message, options.getCodecName());
      bus.sendReply(reply, this, options, replyHandler);
    }
  }

  @Override
  public boolean isSend() {
    return send;
  }

  public void setReplyAddress(String replyAddress) {
    this.replyAddress = replyAddress;
  }

  public MessageCodec<U, V> codec() {
    return messageCodec;
  }

  protected boolean isLocal() {
    return true;
  }
}
