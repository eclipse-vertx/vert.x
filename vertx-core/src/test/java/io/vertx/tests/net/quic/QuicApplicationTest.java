/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.net.quic;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http3.DefaultHttp3DataFrame;
import io.netty.handler.codec.http3.DefaultHttp3HeadersFrame;
import io.netty.handler.codec.http3.Http3;
import io.netty.handler.codec.http3.Http3ClientConnectionHandler;
import io.netty.handler.codec.http3.Http3DataFrame;
import io.netty.handler.codec.http3.Http3HeadersFrame;
import io.netty.handler.codec.http3.Http3RequestStreamInboundHandler;
import io.netty.handler.codec.http3.Http3ServerConnectionHandler;
import io.netty.handler.codec.quic.QuicChannel;
import io.netty.handler.codec.quic.QuicStreamChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.NetUtil;
import io.netty.util.ReferenceCountUtil;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.internal.quic.QuicConnectionInternal;
import io.vertx.core.internal.quic.QuicStreamInternal;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.QuicClient;
import io.vertx.core.net.QuicClientOptions;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicServerOptions;
import io.vertx.core.net.impl.quic.QuicConnectionHandler;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.vertx.tests.net.quic.QuicClientTest.clientOptions;
import static io.vertx.tests.net.quic.QuicServerTest.serverOptions;

@RunWith(LinuxOrOsx.class)
public class QuicApplicationTest extends VertxTestBase {

  @Test
  public void testConnectionLevel() throws Exception {

    // HTTP/3
    byte[] content = "Hello World!\r\n".getBytes(CharsetUtil.US_ASCII);

    QuicServerOptions serverOptions = serverOptions();
    ServerSSLOptions serverSslOptions = serverOptions.getSslOptions();
    serverSslOptions.setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    serverOptions.getTransportOptions().setInitialMaxStreamsUnidirectional(3L);
    serverOptions.getTransportOptions().setInitialMaxStreamDataUnidirectional(1024L);
    QuicServer server = QuicServer.create(vertx, serverOptions);
    server.handler(conn -> {
      QuicConnectionInternal connInternal = (QuicConnectionInternal) conn;
      ChannelPipeline pipeline = connInternal.channelHandlerContext().pipeline();
      pipeline.remove(QuicConnectionHandler.class);
      Http3ServerConnectionHandler http3Handler = new Http3ServerConnectionHandler(
      new ChannelInitializer<QuicStreamChannel>() {
        // Called for each request-stream,
        @Override
        protected void initChannel(QuicStreamChannel ch) {
          ch.pipeline().addLast(new Http3RequestStreamInboundHandler() {

            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
              ReferenceCountUtil.release(frame);
            }

            @Override
            protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
              ReferenceCountUtil.release(frame);
            }

            @Override
            protected void channelInputClosed(ChannelHandlerContext ctx) {
              Http3HeadersFrame headersFrame = new DefaultHttp3HeadersFrame();
              headersFrame.headers().status("404");
              headersFrame.headers().add("server", "netty");
              headersFrame.headers().addInt("content-length", content.length);
              ctx.write(headersFrame);
              ctx.writeAndFlush(new DefaultHttp3DataFrame(Unpooled.wrappedBuffer(content)))
                      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT);
            }
          });
        }
      });
      pipeline.addLast(http3Handler);
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();

    QuicClientOptions clientOptions = clientOptions();
    ClientSSLOptions clientSslOptions = clientOptions.getSslOptions();
    clientSslOptions.setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    clientOptions.getTransportOptions().setInitialMaxStreamsUnidirectional(3L);
    clientOptions.getTransportOptions().setInitialMaxStreamDataUnidirectional(1024L);
    QuicClient client = QuicClient.create(vertx, clientOptions);
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();

    QuicConnectionInternal connection = (QuicConnectionInternal) client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    ChannelPipeline pipeline = connection.channelHandlerContext().pipeline();
    pipeline.remove(QuicConnectionHandler.class);
    pipeline.addLast(new Http3ClientConnectionHandler());

    QuicChannel quicChannel = (QuicChannel) connection.channelHandlerContext().channel();
    QuicStreamChannel streamChannel = Http3.newRequestStream(quicChannel,
      new Http3RequestStreamInboundHandler() {
        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3HeadersFrame frame) {
          ReferenceCountUtil.release(frame);
        }

        @Override
        protected void channelRead(ChannelHandlerContext ctx, Http3DataFrame frame) {
          ReferenceCountUtil.release(frame);
        }

        @Override
        protected void channelInputClosed(ChannelHandlerContext ctx) {
          ctx.close();
        }
      }).sync().getNow();

    // Write the Header frame and send the FIN to mark the end of the request.
    // After this its not possible anymore to write any more data.
    Http3HeadersFrame frame = new DefaultHttp3HeadersFrame();
    frame.headers().method("GET").path("/")
      .authority(NetUtil.LOCALHOST4.getHostAddress() + ":" + 9999)
      .scheme("https");
    streamChannel.writeAndFlush(frame)
      .addListener(QuicStreamChannel.SHUTDOWN_OUTPUT).sync();

    // Wait for the stream channel and quic channel to be closed (this will happen after we received the FIN).
    // After this is done we will close the underlying datagram channel.
    streamChannel.closeFuture().sync();

    // After we received the response lets also close the underlying QUIC channel and datagram channel.
    quicChannel.close().sync();

    client.close().await();
  }

  @Test
  public void testStreamLevel() {
    // HTTP/1.1
    QuicServerOptions serverOptions = serverOptions();
    ServerSSLOptions serverSslOptions = serverOptions.getSslOptions();
    serverSslOptions.setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    serverOptions.getTransportOptions().setInitialMaxStreamsUnidirectional(3L);
    serverOptions.getTransportOptions().setInitialMaxStreamDataUnidirectional(1024L);
    QuicServer server = QuicServer.create(vertx, serverOptions);
    server.handler(connection -> {
      connection.streamHandler(stream -> {
        QuicStreamInternal streamInternal = (QuicStreamInternal) stream;
        ChannelPipeline pipeline = streamInternal.channelHandlerContext().pipeline();
        pipeline.addBefore("handler", "http", new HttpServerCodec());
        pipeline.addBefore("handler", "aggregator", new HttpObjectAggregator(1014));
        streamInternal.messageHandler(msg -> {
          if (msg instanceof FullHttpRequest) {
            ((FullHttpRequest)msg).release();
            DefaultFullHttpResponse response = new DefaultFullHttpResponse(
              HttpVersion.HTTP_1_1,
              HttpResponseStatus.OK,
              Unpooled.copiedBuffer("Hello World", StandardCharsets.UTF_8));
            response.headers().set(HttpHeaders.CONTENT_LENGTH, "11");
            streamInternal.writeMessage(response);
          }
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicClientOptions clientOptions = clientOptions();
    ClientSSLOptions clientSslOptions = clientOptions.getSslOptions();
    clientSslOptions.setApplicationLayerProtocols(Arrays.asList(Http3.supportedApplicationProtocols()));
    clientOptions.getTransportOptions().setInitialMaxStreamsUnidirectional(3L);
    clientOptions.getTransportOptions().setInitialMaxStreamDataUnidirectional(1024L);
    QuicClient client = QuicClient.create(vertx, clientOptions);
    client.bind(SocketAddress.inetSocketAddress(0, "localhost")).await();
    QuicConnectionInternal connection = (QuicConnectionInternal) client.connect(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicStreamInternal stream = (QuicStreamInternal) connection.createStream().await();
    ChannelPipeline pipeline = stream.channelHandlerContext().pipeline();
    pipeline.addBefore("handler", "http", new HttpClientCodec());
    pipeline.addBefore("handler", "aggregator", new HttpObjectAggregator(1014));
    stream.messageHandler(msg -> {
      if (msg instanceof FullHttpResponse) {
        FullHttpResponse response = (FullHttpResponse) msg;
        try {
          assertEquals("Hello World", response.content().toString(StandardCharsets.UTF_8));
        } finally {
          response.release();
        }
        testComplete();
      }
    });
    stream.writeMessage(new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")).await();
    await();
  }
}
