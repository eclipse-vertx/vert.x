/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.http3.Http3Settings;
import io.netty.handler.codec.quic.QuicStreamResetException;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http3.Http3ErrorCode.H3_REQUEST_CANCELLED;

@RunWith(LinuxOrOsx.class)
public class Http3ServerTest extends VertxTestBase {

  public static HttpServerConfig serverConfig() {
    HttpServerConfig options = new HttpServerConfig();
    options.setVersions(Set.of(HttpVersion.HTTP_3));
    options.setQuicPort(4043);
//    options.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    options.setKeyLogFile("/Users/julien/keylogfile.txt");
    return options;

  }

  public static ServerSSLOptions sslOptions() {
    return new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
  }

  private HttpServer server;
  private Http3NettyTest.Client client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = Http3NettyTest.client(new NioEventLoopGroup(1));
    server = vertx.createHttpServer(serverConfig(), sslOptions());
  }

  @Override
  protected void tearDown() throws Exception {
    server.close().await();
    client.close();
    super.tearDown();
  }

  @Test
  public void testGet() throws Exception{

    server.requestHandler(req -> {
      assertEquals(HttpVersion.HTTP_3, req.version());
      req.endHandler(v -> {
        req.response().end("Hello World");
      });
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    assertEquals("Hello World", new String(stream.responseBody()));
  }

  @Test
  public void testPost() throws Exception{

    server.requestHandler(req -> {
      assertEquals(HttpMethod.POST, req.method());
      req.bodyHandler(body -> {
        req.response().end(body);
      });
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.POST("/", "Hello World".getBytes(StandardCharsets.UTF_8));
    assertEquals("Hello World", new String(stream.responseBody()));
  }

  @Test
  public void testTrailers() throws Exception{

    server.requestHandler(req -> {
      req.bodyHandler(body -> {
        // No API to get client trailers
        req.response().end(body);
      });
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();

    stream.write(new DefaultHttp3Headers().method("GET").path("/"));
    stream.write("chunk".getBytes(StandardCharsets.UTF_8));
    stream.end(new DefaultHttp3Headers().set("key", "value"));


    assertEquals("chunk", new String(stream.responseBody()));
  }

  @Test
  public void testUnknownFrame() throws Exception{

    server.requestHandler(req -> {
      Buffer content = Buffer.buffer();
      req.customFrameHandler(frame -> {
        assertEquals(64, frame.type());
        assertEquals(0, frame.flags());
        content.appendBuffer(frame.payload());
      });
      req.endHandler(v -> {
        req.response().end(content);
      });
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();

    stream.write(new DefaultHttp3Headers().method("GET").path("/"));
    stream.writeUnknownFrame(64, "ping".getBytes(StandardCharsets.UTF_8));
    stream.end();

    assertEquals("ping", new String(stream.responseBody()));
  }

  @Test
  public void testServerShutdown() throws Exception{
    testServerConnectionShutdown(false);
  }

  @Test
  public void testServerConnectionShutdown() throws Exception{
    testServerConnectionShutdown(true);
  }

  private void testServerConnectionShutdown(boolean closeConnection) throws Exception{

    disableThreadChecks();
    waitFor(4);

    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      req.response().exceptionHandler(err -> fail());
      HttpConnection connection = req.connection();
      connection.shutdownHandler(v -> {
        complete();
      });
      Future<Void> future;
      if (closeConnection) {
        future = connection.shutdown(10, TimeUnit.SECONDS);
      } else {
        future = server.shutdown(10, TimeUnit.SECONDS);
      }
      future
        .onComplete(onSuccess2(v -> {
          assertTrue(System.currentTimeMillis() - now >= 1000);
          complete();
        }));
      vertx.setTimer(1000, id -> {
        req.response().end("Hello World");
      });
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    connection.goAwayHandler(id -> complete());
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    assertEquals("Hello World", new String(stream.responseBody()));

    await();
  }

  @Test
  public void testServerConnectionShutdownTimeout() throws Exception{

    disableThreadChecks();
    waitFor(3);

    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      req.response().exceptionHandler(err -> {
        assertSame(HttpClosedException.class, err.getClass());
        complete();
      });
      req.connection().shutdown(1, TimeUnit.SECONDS)
        .onComplete(onSuccess2(v -> {
          assertTrue(System.currentTimeMillis() - now <= 2000);
          complete();
        }));
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    List<Long> goAways = Collections.synchronizedList(new ArrayList<>());
    connection.goAwayHandler(id -> {
      goAways.add(id);
      if (id == 0L) {
        complete();
      }
    });
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.resetHandler(code -> {
      assertEquals(H3_REQUEST_CANCELLED.code(), code);
      assertEquals(1, goAways.size());
    });
    stream.GET("/");
    try {
      stream.responseBody();
      fail();
    } catch (QuicStreamResetException e) {
      assertEquals(H3_REQUEST_CANCELLED.code(), e.applicationProtocolCode());
    }

    await();

    Assert.assertEquals(List.of(4L, 0L), goAways);

  }

  @Test
  public void testServerConnectionShutdownRacyStream() throws Exception{

    disableThreadChecks();

    AtomicReference<HttpServerRequest> pendingRequest = new AtomicReference<>();
    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      pendingRequest.compareAndSet(null, req);
      req.connection().shutdown(10, TimeUnit.SECONDS)
        .onComplete(onSuccess2(v -> {
          assertTrue(System.currentTimeMillis() - now <= 1000);
          testComplete();
        }));
    });

    server.listen(8443, "localhost").await();

    CompletableFuture<Void> cont = new CompletableFuture<>();
    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    connection.goAwayHandler(id -> {
      if (id > 0) {
        cont.complete(null);
      }
    });
    Http3NettyTest.Client.Stream stream1 = connection.stream();
    stream1.GET("/");

    cont.get(10, TimeUnit.SECONDS);

    Http3NettyTest.Client.Stream stream2 = connection.stream();
    // Remove validation to simulate a race pretending we have not yet received the go away frame
    stream2.channel().pipeline().remove("Http3RequestStreamValidationHandler#0");
    stream2.GET("/");

    try {
      assertEquals("Hello World", new String(stream2.responseBody()));
      fail();
    } catch (QuicStreamResetException e) {
      assertEquals(Http3ErrorCode.H3_REQUEST_REJECTED.code(), e.applicationProtocolCode());
    }
    pendingRequest.get().response().end();

    await();
  }

  @Test
  public void testServerResetPartialResponse() throws Exception {

    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      response.setChunked(true).write("chunk")
        .onComplete(onSuccess2(v1 -> {
          response.reset(4L).onComplete(onSuccess(v2 -> testComplete()));
      }));
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    try {
      stream.responseBody();
      fail();
    } catch (QuicStreamResetException expected) {
      assertEquals(4L, expected.applicationProtocolCode());
    }

    await();
  }

  @Test
  public void testServerResponseResetUponClientPartialRequestResetByClient() throws Exception {

    CountDownLatch latch = new CountDownLatch(1);

    server.requestHandler(req -> {
      req.exceptionHandler(err -> {
        if (err instanceof StreamResetException) {
          testComplete();
        }
      });
      latch.countDown();
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.write(new DefaultHttp3Headers());

    awaitLatch(latch);
    stream.reset(4);

    try {
      stream.responseBody();
      fail();
    } catch (QuicStreamResetException e) {
      assertEquals(Http3ErrorCode.H3_REQUEST_INCOMPLETE.code(), e.applicationProtocolCode());
    }

    await();
  }

  @Test
  public void testStreamIdleTimeout() throws Exception {

    HttpServerConfig config = serverConfig();
    config.getQuicConfig().setIdleTimeout(Duration.ofMillis(200));
    server = vertx.createHttpServer(config, sslOptions());

    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      req.response().closeHandler(v -> {
        long delta = System.currentTimeMillis() - now;
        System.out.println(delta);
        assertTrue(delta >= 200);
        assertTrue(delta <= 600);
        testComplete();
      });
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    try {
      stream.responseBody();
      fail();
    } catch (Exception e) {
      assertEquals(QuicStreamResetException.class, e.getClass());
      assertEquals(H3_REQUEST_CANCELLED.code(), ((QuicStreamResetException)e).applicationProtocolCode());
    }
    await();
  }

  @Test
  public void testSettings() throws Exception {
    HttpServerConfig config = serverConfig()
      .setHttp3Config(new Http3ServerConfig()
        .setInitialSettings(new io.vertx.core.http.Http3Settings()
          .setMaxFieldSectionSize(1024)
          .setQPackBlockedStreams(1024)
          .setQPackMaxTableCapacity(1024)));
    server = vertx.createHttpServer(config, sslOptions());

    server.connectionHandler(connection -> {
      connection.remoteSettingsHandler(settings -> {
        assertEquals(1024L, (long)settings.get(io.vertx.core.http.Http3Settings.MAX_FIELD_SECTION_SIZE));
        assertEquals(1024L, (long)settings.get(io.vertx.core.http.Http3Settings.QPACK_BLOCKED_STREAMS));
        assertEquals(1024L, (long)settings.get(io.vertx.core.http.Http3Settings.QPACK_MAX_TABLE_CAPACITY));
        testComplete();
      });
    });

    server.requestHandler(req -> {
      req.response().end();
    });

    server.listen(8443, "localhost").await();

    Http3Settings clientSettings = new Http3Settings()
      .maxFieldSectionSize(1024)
      .qpackBlockedStreams(1024)
      .qpackMaxTableCapacity(1024);
    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443), clientSettings);
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    stream.responseBody();
    await();

    assertWaitUntil(() -> connection.remoteSettings() != null);
    Http3Settings settings = connection.remoteSettings();
    assertEquals(1024L, (long)settings.maxFieldSectionSize());
    assertEquals(1024L, (long)settings.qpackMaxTableCapacity());
    assertEquals(1024L, (long)settings.qpackBlockedStreams());
  }

  @Test
  public void testConnect() throws Exception {
    server.requestHandler(req -> {
      assertEquals(HttpMethod.CONNECT, req.method());
      assertEquals("whatever.com", req.authority().host());
      assertNull(req.path());
      assertNull(req.query());
      assertNull(req.scheme());
      assertNull(req.uri());
      assertNull(req.absoluteURI());
      testComplete();
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.write(new DefaultHttp3Headers().method("CONNECT").authority("whatever.com"), true, false);
    await();
  }
}
