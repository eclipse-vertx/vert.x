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

package io.vertx.core.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;

class ReplyHandler<T> extends HandlerRegistration<T> implements Handler<Long> {

  private final Promise<Message<T>> result;
  private final long timeoutID;
  private final long timeout;
  private final String repliedAddress;
  Object trace;

  ReplyHandler(EventBusImpl eventBus, ContextInternal context, String address, String repliedAddress, boolean src, long timeout) {
    super(context, eventBus, address, src);
    this.result = context.promise();
    this.repliedAddress = repliedAddress;
    this.timeoutID = context.setTimer(timeout, this);
    this.timeout = timeout;
  }

  private void trace(Object reply, Throwable failure) {
    VertxTracer tracer = context.tracer();
    Object trace = this.trace;
    if (tracer != null && src && trace != null) {
      tracer.receiveResponse(context, reply, trace, failure, TagExtractor.empty());
    }
  }

  Future<Message<T>> result() {
    return result.future();
  }

  void fail(ReplyException failure) {
    if (context.owner().cancelTimer(timeoutID)) {
      unregister();
      doFail(failure);
    }
  }

  private void doFail(ReplyException failure) {
    trace(null, failure);
    result.fail(failure);
    if (bus.metrics != null) {
      bus.metrics.replyFailure(repliedAddress, failure.failureType());
    }
  }

  @Override
  public void handle(Long id) {
    unregister();
    doFail(new ReplyException(ReplyFailure.TIMEOUT, "Timed out after waiting " + timeout + "(ms) for a reply. address: " + address + ", repliedAddress: " + repliedAddress));
  }

  @Override
  protected void doReceive(Message<T> reply) {
    dispatch(null, reply, context);
  }

  void register() {
    register(false, false, null);
  }

  @Override
  protected void dispatch(Message<T> reply, ContextInternal context, Handler<Message<T>> handler /* null */) {
    if (context.owner().cancelTimer(timeoutID)) {
      unregister();
      if (reply.body() instanceof ReplyException) {
        doFail((ReplyException) reply.body());
      } else {
        trace(reply, null);
        result.complete(reply);
      }
    }
  }
}
