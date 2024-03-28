package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.DeliveryOptions;
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
  protected boolean doReceive(Message msg) {
    if (msg.body() instanceof SynFrame) {
      SynFrame syn = (SynFrame) msg.body();
      String localAddress = eventBus.generateReplyAddress();
      StreamBase ss = new StreamBase(localAddress, context, eventBus, localAddress, false);
      ss.remoteAddress = syn.src;
      ss.base = (MessageImpl) msg;
      PromiseInternal<Void> p = context.promise();
      ss.register(false, false, p);
      p.onComplete(ar -> {
        if (ar.succeeded()) {
          MessageImpl reply = ((MessageImpl)msg).createReply(new SynFrame(localAddress, syn.src), new DeliveryOptions());
          reply.setReplyAddress(localAddress);
          eventBus.sendOrPub(context, reply, new DeliveryOptions(), context.promise());
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
