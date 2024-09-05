/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.benchmarks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMessage;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.Http1xServerConnection;
import io.vertx.core.http.impl.VertxHttpRequestDecoder;
import io.vertx.core.http.impl.VertxHttpResponseEncoder;
import io.vertx.core.internal.ContextInternal;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.impl.VertxHandler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.CompilerControl;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@State(Scope.Thread)
public class HttpServerHandlerBenchmark extends BenchmarkBase {

  @CompilerControl(CompilerControl.Mode.DONT_INLINE)
  public static void consume(final ByteBuf buf) {
  }

  ByteBuf GET;
  int readerIndex;
  int writeIndex;
  VertxInternal vertx;
  EmbeddedChannel vertxChannel;
  EmbeddedChannel nettyChannel;

  public static class Alloc implements ByteBufAllocator {

    private final ByteBuf buf = Unpooled.buffer(512);
    private final int capacity = buf.capacity();

    @Override
    public ByteBuf buffer() {
      buf.clear();
      return buf;
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
      if (initialCapacity <= capacity) {
        return buffer();
      } else {
        throw new IllegalArgumentException("Invalid capacity " + initialCapacity + " > " + capacity);
      }
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
      if (initialCapacity <= capacity) {
        return buffer();
      } else {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public ByteBuf ioBuffer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf ioBuffer(int initialCapacity, int maxCapacity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf heapBuffer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf heapBuffer(int initialCapacity, int maxCapacity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf directBuffer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf directBuffer(int initialCapacity, int maxCapacity) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompositeByteBuf compositeBuffer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompositeByteBuf compositeBuffer(int maxNumComponents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompositeByteBuf compositeHeapBuffer(int maxNumComponents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompositeByteBuf compositeDirectBuffer(int maxNumComponents) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDirectBufferPooled() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int calculateNewCapacity(int minNewCapacity, int maxCapacity) {
      throw new UnsupportedOperationException();
    }
  }


  private static final CharSequence RESPONSE_TYPE_PLAIN = io.vertx.core.http.HttpHeaders.createOptimized("text/plain");

  private static final String HELLO_WORLD = "Hello, world!";
  private static final Buffer HELLO_WORLD_BUFFER = Buffer.buffer(HELLO_WORLD);

  private static final CharSequence HEADER_SERVER = io.vertx.core.http.HttpHeaders.createOptimized("server");
  private static final CharSequence HEADER_DATE = io.vertx.core.http.HttpHeaders.createOptimized("date");
  private static final CharSequence HEADER_CONTENT_TYPE = io.vertx.core.http.HttpHeaders.createOptimized("content-type");
  private static final CharSequence HEADER_CONTENT_LENGTH = io.vertx.core.http.HttpHeaders.createOptimized("content-length");

  private static final CharSequence HELLO_WORLD_LENGTH = io.vertx.core.http.HttpHeaders.createOptimized("" + HELLO_WORLD.length());
  private static final CharSequence SERVER = io.vertx.core.http.HttpHeaders.createOptimized("vert.x");
  private static final CharSequence DATE_STRING = io.vertx.core.http.HttpHeaders.createOptimized(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now()));

  @Setup
  public void setup() {
    vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setDisableTCCL(true));
    HttpServerOptions options = new HttpServerOptions();
    vertxChannel = new EmbeddedChannel(
        new VertxHttpRequestDecoder(options),
        new VertxHttpResponseEncoder());
    vertxChannel.config().setAllocator(new Alloc());

    ContextInternal context = vertx.createEventLoopContext(vertxChannel.eventLoop(), null, Thread.currentThread().getContextClassLoader());
    Handler<HttpServerRequest> app = request -> {
      HttpServerResponse response = request.response();
      MultiMap headers = response.headers();
      headers
          .add(HEADER_CONTENT_TYPE, RESPONSE_TYPE_PLAIN)
          .add(HEADER_SERVER, SERVER)
          .add(HEADER_DATE, DATE_STRING)
          .add(HEADER_CONTENT_LENGTH, HELLO_WORLD_LENGTH);
      response.end(HELLO_WORLD_BUFFER);
    };
    VertxHandler<Http1xServerConnection> handler = VertxHandler.create(chctx -> {
      Http1xServerConnection conn = new Http1xServerConnection(
        () -> context,
              null,
        new HttpServerOptions(),
        chctx,
        context,
        "localhost",
        null);
      conn.handler(app);
      return conn;
    });
    vertxChannel.pipeline().addLast("handler", handler);

    nettyChannel = new EmbeddedChannel(new HttpRequestDecoder(
        options.getMaxInitialLineLength(),
        options.getMaxHeaderSize(),
        options.getMaxChunkSize(),
        false,
        options.getDecoderInitialBufferSize()) {
      @Override
      protected boolean isContentAlwaysEmpty(HttpMessage msg) {
        return false;
      }
    },
        new HttpResponseEncoder() {
          @Override
          public boolean acceptOutboundMessage(Object msg) throws Exception {
            if (msg.getClass() == DefaultFullHttpResponse.class) {
              return true;
            }
            return super.acceptOutboundMessage(msg);
          }
        }, new ChannelInboundHandlerAdapter() {

      private final byte[] STATIC_PLAINTEXT = "Hello, World!".getBytes(CharsetUtil.UTF_8);
      private final int STATIC_PLAINTEXT_LEN = STATIC_PLAINTEXT.length;
      private final ByteBuf PLAINTEXT_CONTENT_BUFFER = Unpooled.unreleasableBuffer(Unpooled.directBuffer().writeBytes(STATIC_PLAINTEXT));
      private final CharSequence PLAINTEXT_CLHEADER_VALUE = new AsciiString(String.valueOf(STATIC_PLAINTEXT_LEN));

      private final CharSequence TYPE_PLAIN = new AsciiString("text/plain");
      private final CharSequence SERVER_NAME = new AsciiString("Netty");
      private final CharSequence CONTENT_TYPE_ENTITY = HttpHeaderNames.CONTENT_TYPE;
      private final CharSequence DATE_ENTITY = HttpHeaderNames.DATE;
      private final CharSequence CONTENT_LENGTH_ENTITY = HttpHeaderNames.CONTENT_LENGTH;
      private final CharSequence SERVER_ENTITY = HttpHeaderNames.SERVER;

      private final DateFormat FORMAT = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z");
      private final CharSequence date = new AsciiString(FORMAT.format(new Date()));

      @Override
      public void channelRead(ChannelHandlerContext ctx, Object o) {
        if (o == LastHttpContent.EMPTY_LAST_CONTENT) {
          return;
        }
        if (o.getClass() == DefaultHttpRequest.class) {
          writeResponse(ctx, (DefaultHttpRequest) o, PLAINTEXT_CONTENT_BUFFER.duplicate(), TYPE_PLAIN, PLAINTEXT_CLHEADER_VALUE);
        } else if (o instanceof HttpRequest) {
          try {
            // slow path
            writeResponse(ctx, (HttpRequest) o, PLAINTEXT_CONTENT_BUFFER.duplicate(), TYPE_PLAIN, PLAINTEXT_CLHEADER_VALUE);
          } finally {
            ReferenceCountUtil.release(o);
          }
        } else {
          ReferenceCountUtil.release(o);
        }
      }

      @Override
      public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
      }

      private void writeResponse(ChannelHandlerContext ctx, HttpRequest request, ByteBuf buf, CharSequence contentType, CharSequence contentLength) {

        // Build the response object.
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf, false);
        HttpHeaders headers = response.headers();
        headers.set(CONTENT_TYPE_ENTITY, contentType);
        headers.set(SERVER_ENTITY, SERVER_NAME);
        headers.set(DATE_ENTITY, date);
        headers.set(CONTENT_LENGTH_ENTITY, contentLength);

        // Close the non-keep-alive connection after the write operation is done.
        ctx.write(response, ctx.voidPromise());
      }
    });
    nettyChannel.config().setAllocator(new Alloc());

    GET = Unpooled.unreleasableBuffer(Unpooled.copiedBuffer((
      "GET / HTTP/1.1\r\n" +
        "\r\n").getBytes()));
    readerIndex = GET.readerIndex();
    writeIndex = GET.writerIndex();
  }

  @Benchmark
  public Object vertx() {
    GET.setIndex(readerIndex, writeIndex);
    vertxChannel.writeInbound(GET);
    return vertxChannel.outboundMessages().poll();
  }

  @Fork(value = 1, jvmArgsAppend = {
      "-Dvertx.threadChecks=false",
      "-Dvertx.disableContextTimings=true",
      "-Dvertx.disableHttpHeadersValidation=true",
      "-Dvertx.disableMetrics=true"
  })
  @Benchmark
  public Object vertxOpt() {
    GET.setIndex(readerIndex, writeIndex);
    vertxChannel.writeInbound(GET);
    return vertxChannel.outboundMessages().poll();
  }

  @Fork(value = 1, jvmArgsAppend = {
    "-Dvertx.threadChecks=false",
    "-Dvertx.disableContextTimings=true",
    "-Dvertx.disableHttpHeadersValidation=true",
    "-Dvertx.disableMetrics=false"
  })
  @Benchmark
  public Object vertxOptMetricsOn() {
    GET.setIndex(readerIndex, writeIndex);
    vertxChannel.writeInbound(GET);
    return vertxChannel.outboundMessages().poll();
  }

  @Benchmark
  public Object netty() {
    GET.setIndex(readerIndex, writeIndex);
    nettyChannel.writeInbound(GET);
    return nettyChannel.outboundMessages().poll();
  }
}
