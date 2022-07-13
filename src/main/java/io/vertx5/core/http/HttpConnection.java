package io.vertx5.core.http;

import io.netty5.buffer.api.BufferAllocator;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.LastHttpContent;
import io.vertx.core.Future;
import io.vertx5.core.buffer.Buffer;
import io.vertx5.core.net.NetSocket;

public abstract class HttpConnection {

  private static final BufferAllocator UNPOOLED_HEAP_ALLOCATOR = BufferAllocator.onHeapUnpooled();

  final NetSocket so;

  HttpConnection(NetSocket so) {
    this.so = so;
  }

  abstract Future<Void> writeObject(HttpObject obj);

  void handleObject(HttpObject obj) {
    if (obj instanceof HttpMessage) {
      handleMessage((HttpMessage) obj);
    }
    if (obj instanceof HttpContent) {
      HttpContent content = (HttpContent) obj;
      io.netty5.buffer.api.Buffer copy = UNPOOLED_HEAP_ALLOCATOR.allocate(content.payload().readableBytes());
      copy.writeBytes(content.payload());
      content.close();
      Buffer buffer = Buffer.buffer(copy);
      handleContent(buffer);
    }
    if (obj instanceof LastHttpContent) {
      handleLast();
    }
  }

  abstract void handleMessage(HttpMessage headers);
  abstract void handleContent(Buffer chunk);
  abstract void handleLast();
}
