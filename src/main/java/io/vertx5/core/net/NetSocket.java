package io.vertx5.core.net;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.channel.ChannelHandler;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.EventLoop;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.util.Resource;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx5.core.buffer.Buffer;

public class NetSocket {

  private static final BufferAllocator UNPOOLED_HEAP_ALLOCATOR = BufferAllocator.onHeapUnpooled();

  private static final Handler<Object> DEFAULT_MSG_HANDLER = msg -> {
    System.out.println("Unhandled message: " + msg);
    if (msg instanceof Resource<?>) {
      ((Resource<?>) msg).close();
    }
  };

  private final ContextInternal context;
  private final SocketChannel channel;
  private boolean readInProgress;
  private boolean needsFlush;
  private Handler<Object> messageHandler;
  private Handler<Void> closeHandler;
  private ChannelHandlerContext channelHandlerContext;

  NetSocket(ContextInternal context, SocketChannel channel) {
    this.context = context;
    this.channel = channel;
  }

  public Context context() {
    return context;
  }

  public ChannelHandlerContext channelHandlerContext() {
    return channelHandlerContext;
  }

  public NetSocket handler(Handler<Buffer> handler) {
    if (handler != null) {
      return messageHandler(msg -> {
        io.netty5.buffer.api.Buffer copy;
        try (io.netty5.buffer.api.Buffer buf = (io.netty5.buffer.api.Buffer) msg) {
          copy = UNPOOLED_HEAP_ALLOCATOR.allocate(buf.readableBytes());
          copy.writeBytes(buf);
        }
        Buffer buffer = Buffer.buffer(copy);
        handler.handle(buffer);
      });
    } else {
      return messageHandler(null);
    }
  }

  public NetSocket messageHandler(Handler<Object> handler) {
    messageHandler = handler;
    return this;
  }

  public Future<Void> write(Buffer buffer) {
    return writeMessage(buffer.unwrap());
  }

  public Future<Void> writeMessage(Object msg) {
    PromiseInternal<Void> promise = context.promise();
    EventLoop eventLoop = context.nettyEventLoop();
    if (eventLoop.inEventLoop()) {
      writeMessage(msg, promise);
    } else {
      eventLoop.execute(() -> NetSocket.this.writeMessage(msg, promise));
    }
    return promise.future();
  }

  private void writeMessage(Object msg, PromiseInternal<Void> promise) {
    io.netty5.util.concurrent.Future<Void> fut;
    if (readInProgress) {
      needsFlush = true;
      fut = channelHandlerContext.write(msg);
    } else {
      fut = channelHandlerContext.writeAndFlush(msg);
    }
    fut.addListener(future -> {
      if (future.isSuccess()) {
        promise.complete();
      } else {
        promise.fail(future.cause());
      }
    });
  }


  public NetSocket closeHandler(Handler<Void> handler) {
    closeHandler = handler;
    return this;
  }

  final ChannelHandler handler = new ChannelHandler() {
    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
      channelHandlerContext = ctx;
    }
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
      channelHandlerContext = null;
    }
    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
      readInProgress = false;
      if (needsFlush) {
        needsFlush = false;
        ctx.flush();
      }
    }
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
      readInProgress = true;
      Handler<Object> handler = messageHandler;
      if (handler == null) {
        handler = DEFAULT_MSG_HANDLER;
      }
      context.emit(msg, handler);
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
