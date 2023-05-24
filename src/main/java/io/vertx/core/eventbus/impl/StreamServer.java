package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageStream;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;

class StreamServer extends HandlerRegistration {
  private final EventBusImpl eventBus;
  private final Handler<MessageStream> handler;

  public StreamServer(EventBusImpl eventBus, ContextInternal ctx, String address, Handler<MessageStream> handler) {
    super(ctx, eventBus, address, false);
    this.eventBus = eventBus;
    this.handler = handler;
  }

  @Override
  protected boolean doReceive(Frame frame) {
    if (frame instanceof SynFrame) {
      SynFrame syn = (SynFrame) frame;
      String localAddress = eventBus.generateReplyAddress();
      StreamBase ss = new StreamBase(localAddress, context, eventBus, localAddress, false);
      ss.remoteAddress = syn.src;
      PromiseInternal<Void> p = context.promise();
      ss.register(false, true, p);
      p.onComplete(ar -> {
        if (ar.succeeded()) {
          SynFrame reply = new SynFrame(localAddress, syn.src);
          eventBus.sendLocally(reply, context.promise());
          handler.handle(ss);
        }
      });
    }
    return true;
  }

  @Override
  protected void dispatch(Message msg, ContextInternal context, Handler handler) {
  }
}
