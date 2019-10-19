package io.vertx.core.eventbus.impl;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.ReplyException;
import io.vertx.core.eventbus.ReplyFailure;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.spi.tracing.TagExtractor;
import io.vertx.core.spi.tracing.VertxTracer;

class ReplyHandler<T> extends HandlerRegistration<T> implements Handler<Message<T>> {

  private final EventBusImpl eventBus;
  private final ContextInternal context;
  private final Promise<Message<T>> result;
  private final long timeoutID;
  private final boolean src;
  private final String repliedAddress;
  Object trace;

  ReplyHandler(EventBusImpl eventBus, ContextInternal context, String address, String repliedAddress, boolean src, long timeout) {
    super(context, eventBus, address, src);
    this.eventBus = eventBus;
    this.context = context;
    this.result = Promise.promise();
    this.src = src;
    this.repliedAddress = repliedAddress;
    this.timeoutID = eventBus.vertx.setTimer(timeout, id -> {
      fail(new ReplyException(ReplyFailure.TIMEOUT, "Timed out after waiting " + timeout + "(ms) for a reply. address: " + address + ", repliedAddress: " + repliedAddress));
    });
  }

  private void trace(Object reply, Throwable failure) {
    VertxTracer tracer = context.tracer();
    if (tracer != null && src) {
      tracer.receiveResponse(context, reply, trace, failure, TagExtractor.empty());
    }
  }

  Future<Message<T>> result() {
    return result.future();
  }

  void fail(ReplyException failure) {
    unregister(ar -> {});
    if (eventBus.metrics != null) {
      eventBus.metrics.replyFailure(repliedAddress, failure.failureType());
    }
    trace(null, failure);
    result.tryFail(failure);
  }


  @Override
  protected void doReceive(Message<T> reply) {
    dispatch(this, reply, context);
  }

  @Override
  protected void doUnregister() {
  }

  void register() {
    register(repliedAddress, true);
  }

  @Override
  public void handle(Message<T> reply) {
    eventBus.vertx.cancelTimer(timeoutID);
    if (reply.body() instanceof ReplyException) {
      // This is kind of clunky - but hey-ho
      fail((ReplyException) reply.body());
    } else {
      trace(reply, null);
      result.complete(reply);
    }
  }
}
