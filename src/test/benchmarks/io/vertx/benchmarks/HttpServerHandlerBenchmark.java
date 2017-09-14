/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.benchmarks;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpRequestDecoder;
import io.netty.handler.codec.http.HttpResponseEncoder;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AsciiString;
import io.netty.util.CharsetUtil;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.impl.HttpHandlers;
import io.vertx.core.http.impl.ServerHandler;
import io.vertx.core.impl.ContextImpl;
import io.vertx.core.impl.EventLoopContext;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.HandlerHolder;
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

  static class Alloc implements ByteBufAllocator {

    private final ByteBuf buf = Unpooled.buffer();
    private final int capacity = buf.capacity();

    @Override
    public ByteBuf buffer() {
      buf.clear();
      return buf;
    }

    @Override
    public ByteBuf buffer(int initialCapacity) {
      if (initialCapacity < capacity) {
        return buffer();
      } else {
        throw new IllegalArgumentException();
      }
    }

    @Override
    public ByteBuf buffer(int initialCapacity, int maxCapacity) {
      if (initialCapacity < capacity) {
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
    vertx = (VertxInternal) Vertx.vertx();
    HttpServerOptions options = new HttpServerOptions();
    vertxChannel = new EmbeddedChannel(
        new HttpRequestDecoder(
            options.getMaxInitialLineLength(),
            options.getMaxHeaderSize(),
            options.getMaxChunkSize(),
            false,
            options.getDecoderInitialBufferSize()),
        new HttpResponseEncoder()
    );
    vertxChannel.config().setAllocator(new Alloc());
    ContextImpl context = new EventLoopContext(vertx, vertxChannel.eventLoop(), null, null, null, new JsonObject(), Thread.currentThread().getContextClassLoader());
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
    HandlerHolder<HttpHandlers> holder = new HandlerHolder<>(context, new HttpHandlers(app, null, null, null));
    ServerHandler handler = new ServerHandler(null, new HttpServerOptions(), "localhost", holder, null);
    vertxChannel.pipeline().addLast("handler", handler);

    nettyChannel = new EmbeddedChannel(new HttpRequestDecoder(
        options.getMaxInitialLineLength(),
        options.getMaxHeaderSize(),
        options.getMaxChunkSize(),
        false,
        options.getDecoderInitialBufferSize()),
        new HttpResponseEncoder(), new SimpleChannelInboundHandler<HttpRequest>() {

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
      protected void channelRead0(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {
        writeResponse(ctx, msg, PLAINTEXT_CONTENT_BUFFER.duplicate(), TYPE_PLAIN, PLAINTEXT_CLHEADER_VALUE);
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
  public void vertx() {
    GET.setIndex(readerIndex, writeIndex);
    vertxChannel.writeInbound(GET);
    ByteBuf result = (ByteBuf) vertxChannel.outboundMessages().poll();
    consume(result);
  }

  @Fork(value = 1, jvmArgsAppend = {
      "-Dvertx.threadChecks=false",
      "-Dvertx.disableContextTimings=true",
      "-Dvertx.disableTCCL=true ",
  })
  @Benchmark
  public void vertxOpt() {
    GET.setIndex(readerIndex, writeIndex);
    vertxChannel.writeInbound(GET);
    ByteBuf result = (ByteBuf) vertxChannel.outboundMessages().poll();
    consume(result);
  }

  @Benchmark
  public void netty() {
    GET.setIndex(readerIndex, writeIndex);
    nettyChannel.writeInbound(GET);
    ByteBuf result = (ByteBuf) nettyChannel.outboundMessages().poll();
    consume(result);
  }
}
