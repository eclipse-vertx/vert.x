package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageStream;
import io.vertx.core.impl.ContextInternal;

import java.util.concurrent.TimeoutException;

class ClientStream extends StreamBase implements Handler<Long> {

  private final Promise<MessageStream> promise2;
  private final long timeoutID;

  public ClientStream(EventBusImpl eventBus, String sourceAddress, ContextInternal ctx, Promise<MessageStream> promise2) {
    super(sourceAddress, ctx, eventBus, sourceAddress, true);
    this.promise2 = promise2;
    this.timeoutID = ctx.setTimer(3_000, this);
  }

  @Override
  public void handle(Long event) {
    unregister();
    promise2.fail(new TimeoutException());
  }

  @Override
  protected boolean doReceive(Message msg) {
    if (msg.body() instanceof SynFrame) {
      if (context.owner().cancelTimer(timeoutID)) {
        base = (MessageImpl) msg;
        SynFrame syn = (SynFrame) msg.body();
        remoteAddress = syn.src;
        promise2.complete(this);
      }
      return true;
    } else {
      if (base != null) {
        return super.doReceive(msg);
      } else {
        if (context.owner().cancelTimer(timeoutID)) {
          unregister();
          promise2.fail(new IllegalStateException());
        }
        return true;
      }
    }
  }
}
