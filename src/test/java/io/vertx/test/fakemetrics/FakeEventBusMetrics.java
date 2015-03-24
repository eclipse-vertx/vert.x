/*
 * Copyright (c) 2011-2013 The original author or authors
 *  ------------------------------------------------------
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *      The Eclipse Public License is available at
 *      http://www.eclipse.org/legal/epl-v10.html
 *
 *      The Apache License v2.0 is available at
 *      http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.fakemetrics;

import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.spi.metrics.EventBusMetrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class FakeEventBusMetrics implements EventBusMetrics<HandlerRegistration> {

  private final List<SentMessage> sentMessages = Collections.synchronizedList(new ArrayList<>());
  private final List<ReceivedMessage> receivedMessages = Collections.synchronizedList(new ArrayList<>());
  private final List<HandlerRegistration> registrations = new ArrayList<>();

  public List<SentMessage> getSentMessages() {
    return sentMessages;
  }

  public List<ReceivedMessage> getReceivedMessages() {
    return receivedMessages;
  }

  public List<HandlerRegistration> getRegistrations() {
    return registrations;
  }

  @Override
  public HandlerRegistration handlerRegistered(String address, boolean replyHandler) {
    HandlerRegistration registration = new HandlerRegistration(address, replyHandler);
    registrations.add(registration);
    return registration;
  }

  public void handlerUnregistered(HandlerRegistration handler) {
    registrations.remove(handler);
  }

  public void beginHandleMessage(HandlerRegistration handler) {
    handler.beginCount.incrementAndGet();
  }

  public void endHandleMessage(HandlerRegistration handler, Throwable failure) {
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

  public void replyFailure(String address, ReplyFailure failure) {
    throw new UnsupportedOperationException();
  }

  public String baseName() {
    throw new UnsupportedOperationException();
  }

  public boolean isEnabled() {
    throw new UnsupportedOperationException();
  }

  public void close() {
    throw new UnsupportedOperationException();
  }
}
