package io.vertx5.core.net;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.socket.SocketChannel;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx5.core.buffer.Buffer;

public class NetSocket {

  private static final BufferAllocator UNPOOLED_HEAP_ALLOCATOR = BufferAllocator.onHeapUnpooled();

  private final ContextInternal context;
  private final SocketChannel channel;
  private Handler<Buffer> messageHandler;
  private Handler<Void> closeHandler;

  NetSocket(ContextInternal context, SocketChannel channel) {
    this.context = context;
    this.channel = channel;
  }

  public NetSocket handler(Handler<Buffer> handler) {
    messageHandler = handler;
    return this;
  }

  public Future<Void> write(Buffer buffer) {
    io.netty5.buffer.api.Buffer copy = buffer.getByteBuf();
    io.netty5.util.concurrent.Future<Void> fut = channel.writeAndFlush(copy);
    PromiseInternal<Void> promise = context.promise();
    fut.addListener(future -> {
      if (future.isSuccess()) {
        promise.complete();
      } else {
        promise.fail(future.cause());
      }
    });
    return promise.future();
  }

  public NetSocket closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  final ChannelHandler handler = new ChannelHandler() {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
      io.netty5.buffer.api.Buffer buf = (io.netty5.buffer.api.Buffer) msg;
      io.netty5.buffer.api.Buffer copy = UNPOOLED_HEAP_ALLOCATOR.allocate(buf.readableBytes());
      copy.writeBytes(buf);
      buf.close();
      Buffer buffer = Buffer.buffer(copy);
      context.emit(buffer, messageHandler);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
      context.emit(v -> {
        Handler<Void> handler = closeHandler;
        if (handler != null) {
          handler.handle(null);
        }
      });
    }
  };
}
