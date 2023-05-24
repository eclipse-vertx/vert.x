package io.vertx.core.eventbus.impl;

import io.vertx.core.Promise;
import io.vertx.core.eventbus.MessageStream;
import io.vertx.core.impl.ContextInternal;

class ClientStream extends StreamBase {

  private final Promise<MessageStream> promise2;

  public ClientStream(EventBusImpl eventBus, String sourceAddress, ContextInternal ctx, Promise<MessageStream> promise2) {
    super(sourceAddress, ctx, eventBus, sourceAddress, true);
    this.promise2 = promise2;
  }

  @Override
  protected boolean doReceive(Frame frame) {
    if (frame instanceof SynFrame) {
      SynFrame syn = (SynFrame) frame;
      remoteAddress = syn.src;
      promise2.complete(this);
      return true;
    } else {
      return super.doReceive(frame);
    }
  }
}
