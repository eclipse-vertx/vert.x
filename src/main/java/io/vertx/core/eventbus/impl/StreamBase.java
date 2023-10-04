package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageStream;
import io.vertx.core.impl.ContextInternal;

class StreamBase extends HandlerRegistration implements MessageStream {

  private Handler<Message<String>> handler;
  private Handler<Void> endHandler;
  final String localAddress;
  String remoteAddress;
  private boolean halfClosed;

  StreamBase(String localAddress, ContextInternal context, EventBusImpl bus, String address, boolean src) {
    super(context, bus, address, src);
    this.localAddress = localAddress;
  }

  @Override
  protected boolean doReceive(Frame frame) {
    if (frame instanceof MessageImpl) {
      MessageImpl msg = (MessageImpl) frame;
      Handler<Message<String>> h = handler;
      if (h != null) {
        h.handle(msg);
      }
    } else if (frame instanceof FinFrame) {
      Handler<Void> h = endHandler;
      if (h != null) {
        h.handle(null);
      }
      if (halfClosed) {
        unregister();
      } else {
        halfClosed = true;
      }
    }
    return true;
  }

  @Override
  protected void dispatch(Message msg, ContextInternal context, Handler handler) {

  }

  @Override
  public void handler(Handler<Message<String>> handler) {
    this.handler = handler;
  }

  @Override
  public void endHandler(Handler<Void> handler) {
    this.endHandler = handler;
  }

  @Override
  public void write(String body) {
    MessageImpl msg = new MessageImpl(remoteAddress, MultiMap.caseInsensitiveMultiMap(), body, CodecManager.STRING_MESSAGE_CODEC, true, bus);
    bus.sendLocally(msg, context.promise());
  }

  @Override
  public void end() {
    FinFrame fin = new FinFrame(remoteAddress);
    bus.sendLocally(fin, context.promise());
    if (halfClosed) {
      unregister();
    } else {
      halfClosed = true;
    }
  }
}
