package io.vertx5.core.http;

import io.netty5.handler.codec.http.DefaultHttpContent;
import io.netty5.handler.codec.http.DefaultLastHttpContent;
import io.netty5.handler.codec.http.FullHttpMessage;
import io.netty5.handler.codec.http.HttpContent;
import io.netty5.handler.codec.http.HttpMessage;
import io.netty5.handler.codec.http.HttpObject;
import io.netty5.handler.codec.http.LastHttpContent;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.ContextInternal;
import io.vertx5.core.buffer.Buffer;
import io.vertx5.core.buffer.impl.BufferImpl;

import java.util.LinkedHashMap;
import java.util.Map;

public abstract class HttpStream {

  private final HttpConnection connection;
  private final Map<String, String> requestHeaders = new LinkedHashMap<>();
  private final Map<String, String> responseHeaders = new LinkedHashMap<>();
  private boolean headersSent;
  private boolean footersSent;
  private Handler<Buffer> chunkHandler;
  private Handler<Void> endHandler;

  HttpStream(HttpConnection connection) {
    this.connection = connection;
  }

  public Map<String, String> requestHeaders() {
    return requestHeaders;
  }
  public Map<String, String> responseHeaders() {
    return responseHeaders;
  }

  abstract HttpMessage createPartialMessage(io.netty5.buffer.api.Buffer payload);
  LastHttpContent<?> createLastHttpContent(io.netty5.buffer.api.Buffer payload) {
    return new DefaultLastHttpContent(payload);
  }
  abstract FullHttpMessage<?> createFullMessage(io.netty5.buffer.api.Buffer payload);
  HttpContent<?> createContent(io.netty5.buffer.api.Buffer payload) {
    return new DefaultHttpContent(payload);
  }

  public HttpStream handler(Handler<Buffer> handler) {
    this.chunkHandler = handler;
    return this;
  }

  public HttpStream endHandler(Handler<Void> handler) {
    endHandler = handler;
    return this;
  }
  public Future<Void> end() {
    return write(Buffer.buffer(), true);
  }

  public Future<Void> end(Buffer buffer) {
    return write(buffer, true);
  }

  public Future<Void> write(Buffer buffer) {
    return write(buffer, false);
  }

  private Future<Void> write(Buffer buffer, boolean end) {
    if (footersSent) {
      throw new IllegalStateException();
    }
    BufferImpl vertxBuffer = (BufferImpl) buffer;
    HttpObject obj;
    io.netty5.buffer.api.Buffer payload = vertxBuffer.getByteBuf();
    if (headersSent) {
      if (end) {
        footersSent = true;
        obj = createLastHttpContent(payload);
      } else {
        obj = createContent(payload);
      }
    } else {
      headersSent = true;
      if (end) {
        footersSent = true;
        HttpMessage msg = createFullMessage(payload);
        msg.headers().add("content-length", "" + payload.readableBytes());
        obj = msg;
      } else {
        HttpMessage msg = createPartialMessage(payload);
        msg.headers().set("transfer-encoding", "chunked");
        obj = msg;
      }
    }
    return connection.writeObject(obj);
  }

  void handleContent(Buffer chunk) {
    ContextInternal ctx = (ContextInternal) connection.so.context();
    ctx.emit(chunk, buffer -> {
      Handler<Buffer> handler = chunkHandler;
      if (handler != null) {
        handler.handle(buffer);
      }
    });
  }

  void handleLast() {
    ContextInternal ctx = (ContextInternal) connection.so.context();
    ctx.emit(v -> {
      Handler<Void> handler = endHandler;
      if (handler != null) {
        handler.handle(null);
      }
    });
  }

  static void copyHeaders(Map<String, String> headers, HttpMessage msg) {
    for (Map.Entry<String, String> header : headers.entrySet()) {
      msg.headers().set(header.getKey(), header.getValue());
    }
  }
  static void copyHeaders(HttpMessage msg, Map<String, String> headers) {
    msg.headers().forEach(header -> headers.put(header.getKey(), header.getValue()));
  }

}
