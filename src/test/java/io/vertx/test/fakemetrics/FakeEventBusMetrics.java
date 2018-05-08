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

package io.vertx.test.fakemetrics;

import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.spi.metrics.EventBusMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeEventBusMetrics extends FakeMetricsBase implements EventBusMetrics<HandlerMetric> {

  private final List<SentMessage> sentMessages = Collections.synchronizedList(new ArrayList<>());
  private final List<ReceivedMessage> receivedMessages = Collections.synchronizedList(new ArrayList<>());
  private final List<HandlerMetric> registrations = new ArrayList<>();
  private final Map<String, AtomicInteger> encoded = new ConcurrentHashMap<>();
  private final Map<String, AtomicInteger> decoded = new ConcurrentHashMap<>();
  private final List<String> replyFailureAddresses = Collections.synchronizedList(new ArrayList<>());
  private final List<ReplyFailure> replyFailures = Collections.synchronizedList(new ArrayList<>());

  public Map<String, AtomicInteger> getEncoded() {
    return encoded;
  }

  public Map<String, AtomicInteger> getDecoded() {
    return decoded;
  }

  public List<SentMessage> getSentMessages() {
    return sentMessages;
  }

  public List<ReceivedMessage> getReceivedMessages() {
    return receivedMessages;
  }

  public List<HandlerMetric> getRegistrations() {
    return registrations;
  }

  public List<String> getReplyFailureAddresses() {
    return replyFailureAddresses;
  }

  public List<ReplyFailure> getReplyFailures() {
    return replyFailures;
  }

  public int getEncodedBytes(String address) {
    AtomicInteger value = encoded.get(address);
    return value != null ? value.get() : 0;
  }

  public int getDecodedBytes(String address) {
    AtomicInteger value = decoded.get(address);
    return value != null ? value.get() : 0;
  }

  @Override
  public HandlerMetric handlerRegistered(String address, String repliedAddress) {
    HandlerMetric registration = new HandlerMetric(address, repliedAddress);
    registrations.add(registration);
    return registration;
  }

  public void handlerUnregistered(HandlerMetric handler) {
    registrations.remove(handler);
  }

  @Override
  public void scheduleMessage(HandlerMetric handler, boolean local) {
    handler.scheduleCount.incrementAndGet();
    if (local) {
      handler.localScheduleCount.incrementAndGet();
    }
  }

  @Override
  public void beginHandleMessage(HandlerMetric handler, boolean local) {
    handler.beginCount.incrementAndGet();
    if (local) {
      handler.localBeginCount.incrementAndGet();
    }
  }

  public void endHandleMessage(HandlerMetric handler, Throwable failure) {
    handler.endCount.incrementAndGet();
    if (failure != null) {
      handler.failureCount.incrementAndGet();
    }
  }

  @Override
  public void messageSent(String address, boolean publish, boolean local, boolean remote) {
    sentMessages.add(new SentMessage(address, publish, local, remote));
  }

  @Override
  public void messageReceived(String address, boolean publish, boolean local, int handlers) {
    receivedMessages.add(new ReceivedMessage(address, publish, local, handlers));
  }

  @Override
  public void messageWritten(String address, int numberOfBytes) {
    AtomicInteger value = new AtomicInteger();
    AtomicInteger existing = encoded.putIfAbsent(address, value);
    if (existing != null) {
      value = existing;
    }
    value.addAndGet(numberOfBytes);
  }

  @Override
  public void messageRead(String address, int numberOfBytes) {
    AtomicInteger value = new AtomicInteger();
    AtomicInteger existing = decoded.putIfAbsent(address, value);
    if (existing != null) {
      value = existing;
    }
    value.addAndGet(numberOfBytes);
  }

  public void replyFailure(String address, ReplyFailure failure) {
    replyFailureAddresses.add(address);
    replyFailures.add(failure);
  }

  public boolean isEnabled() {
    return true;
  }

}
