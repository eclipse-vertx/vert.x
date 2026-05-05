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

import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3ErrorCode;
import io.netty.handler.codec.http3.Http3Settings;
import io.netty.handler.codec.quic.QuicStreamResetException;
import io.netty.util.NetUtil;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.QuicConnection;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicStream;
import io.vertx.core.net.ServerSSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.test.core.TestUtils;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static io.netty.handler.codec.http3.Http3ErrorCode.H3_REQUEST_CANCELLED;

public class Http3ServerTest extends VertxTestBase {

  public Http3ServerTest() {
    super(ReportMode.FORBIDDEN);
  }

  public static HttpServerConfig serverConfig() {
    HttpServerConfig options = new HttpServerConfig();
    options.setVersions(HttpVersion.HTTP_3);
    options.setQuicPort(4043);
//    options.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    options.setKeyLogFile("/Users/julien/keylogfile.txt");
    return options;

  }

  public static ServerSSLOptions sslOptions() {
    return new ServerSSLOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
  }

  private HttpServer server;
  private Http3TestClient.Client client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = Http3TestClient.client(vertx);
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
      Assert.assertEquals(HttpVersion.HTTP_3, req.version());
      req.endHandler(v -> {
        req.response().end("Hello World");
      });
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.GET("/");
    Assert.assertEquals("Hello World", new String(stream.responseBody()));
  }

  @Test
  public void testPost() throws Exception{

    server.requestHandler(req -> {
      Assert.assertEquals(HttpMethod.POST, req.method());
      req.bodyHandler(body -> {
        req.response().end(body);
      });
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.POST("/", "Hello World".getBytes(StandardCharsets.UTF_8));
    Assert.assertEquals("Hello World", new String(stream.responseBody()));
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

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();

    stream.write(new DefaultHttp3Headers().method("GET").path("/"));
    stream.write("chunk".getBytes(StandardCharsets.UTF_8));
    stream.end(new DefaultHttp3Headers().set("key", "value"));


    Assert.assertEquals("chunk", new String(stream.responseBody()));
  }

  @Test
  public void testUnknownFrame() throws Exception{

    server.requestHandler(req -> {
      Buffer content = Buffer.buffer();
      req.customFrameHandler(frame -> {
        Assert.assertEquals(64, frame.type());
        Assert.assertEquals(0, frame.flags());
        content.appendBuffer(frame.payload());
      });
      req.endHandler(v -> {
        req.response().end(content);
      });
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();

    stream.write(new DefaultHttp3Headers().method("GET").path("/"));
    stream.writeUnknownFrame(64, "ping".getBytes(StandardCharsets.UTF_8));
    stream.end();

    Assert.assertEquals("ping", new String(stream.responseBody()));
  }

  @Test
  public void testServerShutdown() throws Exception{
    // See https://github.com/netty/netty/issues/16718
    Assume.assumeFalse("Not supported yet with io_uring", TRANSPORT == Transport.IO_URING);
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
      req.response().exceptionHandler(err -> Assert.fail());
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
        .onComplete(TestUtils.onSuccess2(v -> {
          Assert.assertTrue(System.currentTimeMillis() - now >= 1000);
          complete();
        }));
      vertx.setTimer(1000, id -> {
        req.response().end("Hello World");
      });
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    connection.goAwayHandler(id -> complete());
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.GET("/");
    Assert.assertEquals("Hello World", new String(stream.responseBody()));

    await();
  }

  @Test
  public void testServerConnectionShutdownTimeout() throws Exception{

    disableThreadChecks();
    waitFor(3);

    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      req.response().exceptionHandler(err -> {
        Assert.assertSame(HttpClosedException.class, err.getClass());
        complete();
      });
      req.connection().shutdown(1, TimeUnit.SECONDS)
        .onComplete(TestUtils.onSuccess2(v -> {
          Assert.assertTrue(System.currentTimeMillis() - now <= 2000);
          complete();
        }));
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    List<Long> goAways = Collections.synchronizedList(new ArrayList<>());
    connection.goAwayHandler(id -> {
      goAways.add(id);
      if (id == 0L) {
        complete();
      }
    });
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.resetHandler(code -> {
      Assert.assertEquals(H3_REQUEST_CANCELLED.code(), (long)code);
      Assert.assertEquals(1, goAways.size());
    });
    stream.GET("/");
    try {
      stream.responseBody();
      Assert.fail();
    } catch (QuicStreamResetException e) {
      Assert.assertEquals(H3_REQUEST_CANCELLED.code(), e.applicationProtocolCode());
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
        .onComplete(TestUtils.onSuccess2(v -> {
          Assert.assertTrue(System.currentTimeMillis() - now <= 1000);
          testComplete();
        }));
    });

    server.listen(8443, "localhost").await();

    CompletableFuture<Void> cont = new CompletableFuture<>();
    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    connection.goAwayHandler(id -> {
      if (id > 0) {
        cont.complete(null);
      }
    });
    Http3TestClient.Client.Stream stream1 = connection.stream();
    stream1.GET("/");

    cont.get(10, TimeUnit.SECONDS);

    Http3TestClient.Client.Stream stream2 = connection.stream();
    // Remove validation to simulate a race pretending we have not yet received the go away frame
    stream2.channel().pipeline().remove("Http3RequestStreamValidationHandler#0");
    stream2.GET("/");

    try {
      Assert.assertEquals("Hello World", new String(stream2.responseBody()));
      Assert.fail();
    } catch (QuicStreamResetException e) {
      Assert.assertEquals(Http3ErrorCode.H3_REQUEST_REJECTED.code(), e.applicationProtocolCode());
    }
    pendingRequest.get().response().end();

    await();
  }

  @Test
  public void testServerResetPartialResponse() throws Exception {

    server.requestHandler(req -> {
      HttpServerResponse response = req.response();
      response.setChunked(true).write("chunk")
        .onComplete(TestUtils.onSuccess2(v1 -> {
          response.reset(4L).onComplete(TestUtils.onSuccess(v2 -> testComplete()));
      }));
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.GET("/");
    try {
      stream.responseBody();
      Assert.fail();
    } catch (QuicStreamResetException expected) {
      Assert.assertEquals(4L, expected.applicationProtocolCode());
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

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.write(new DefaultHttp3Headers());

    TestUtils.awaitLatch(latch);
    stream.reset(4);

    try {
      stream.responseBody();
      Assert.fail();
    } catch (QuicStreamResetException e) {
      Assert.assertEquals(Http3ErrorCode.H3_REQUEST_INCOMPLETE.code(), e.applicationProtocolCode());
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
        Assert.assertTrue(delta >= 200);
        Assert.assertTrue(delta <= 600);
        testComplete();
      });
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.GET("/");
    try {
      stream.responseBody();
      Assert.fail();
    } catch (Exception e) {
      Assert.assertEquals(QuicStreamResetException.class, e.getClass());
      Assert.assertEquals(H3_REQUEST_CANCELLED.code(), ((QuicStreamResetException)e).applicationProtocolCode());
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
        Assert.assertEquals(1024L, (long)settings.get(io.vertx.core.http.Http3Settings.MAX_FIELD_SECTION_SIZE));
        Assert.assertEquals(1024L, (long)settings.get(io.vertx.core.http.Http3Settings.QPACK_BLOCKED_STREAMS));
        Assert.assertEquals(1024L, (long)settings.get(io.vertx.core.http.Http3Settings.QPACK_MAX_TABLE_CAPACITY));
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
    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443), clientSettings);
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.GET("/");
    stream.responseBody();
    await();

    assertWaitUntil(() -> connection.remoteSettings() != null);
    Http3Settings settings = connection.remoteSettings();
    Assert.assertEquals(1024L, (long)settings.maxFieldSectionSize());
    Assert.assertEquals(1024L, (long)settings.qpackMaxTableCapacity());
    Assert.assertEquals(1024L, (long)settings.qpackBlockedStreams());
  }

  @Test
  public void testConnect() throws Exception {
    server.requestHandler(req -> {
      Assert.assertEquals(HttpMethod.CONNECT, req.method());
      Assert.assertEquals("whatever.com", req.authority().host());
      Assert.assertNull(req.path());
      Assert.assertNull(req.query());
      Assert.assertNull(req.scheme());
      Assert.assertNull(req.uri());
      Assert.assertNull(req.absoluteURI());
      testComplete();
    });

    server.listen(8443, "localhost").await();

    Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3TestClient.Client.Stream stream = connection.stream();
    stream.write(new DefaultHttp3Headers().method("CONNECT").authority("whatever.com"), true, false);
    await();
  }

  @Test
  public void testServerSharing() throws Exception {
    VertxInternal vxi = (VertxInternal) vertx;
    int num = 3;
    List<Http3TestClient.Client.Connection> connections = new ArrayList<>();
    Set<HttpConnection> serverConnections = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < num;i++) {
      HttpServerConfig config = serverConfig();
      config.getQuicConfig().setLoadBalanced(true);
      HttpServer server = vertx.createHttpServer(config, sslOptions());
      server.connectionHandler(serverConnections::add);
      server.requestHandler(request -> {
        request.response().end("Hello World " + request.connection().localAddress().port());
      });
      Context ctx = vxi.createEventLoopContext();
      Future.future(p -> ctx.runOnContext(v -> server
          .listen(SocketAddress.inetSocketAddress(8443, "localhost"))
          .onComplete(p)))
        .await();
      Http3TestClient.Client.Connection connection = client.connect(new InetSocketAddress("localhost", 8443));
      connections.add(connection);
    }
    Assert.assertEquals(num, serverConnections.size());
    for (Http3TestClient.Client.Connection connection : connections) {
      Http3TestClient.Client.Stream stream = connection.stream();
      stream.GET("/");
      byte[] body = stream.responseBody();
      Assert.assertEquals("Hello World 8443", new String(body));
    }
  }

  //  @Test
//  public void testNetworkLogging() {
//    TestLoggerFactory factory = TestUtils.testLogging(() -> {
//      server.close();
//      server = vertx.createHttpServer(serverConfig().setLogConfig(new LogConfig().setEnabled(true)), sslOptions());
//      server.requestHandler(req -> {
//        req.response().end("Hello World");
//      });
//      server.listen(8443, "localhost").await();
//      try {
//        Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
//        Http3NettyTest.Client.Stream stream = connection.stream();
//        stream.POST("/", "Hello World".getBytes());
//        assertEquals("Hello World", new String(stream.responseBody()));
//        server.shutdown(Duration.ofMillis(100)).await();
//      } catch (Exception e) {
//        fail(e);
//      }
//    });
//    assertTrue(factory.hasName(Http3FrameLogger.class.getName()));
//    assertEquals(7, factory
//      .logs(Http3FrameLogger.class)
//      .count());
//    assertEquals(2, factory.
//      logs(Http3FrameLogger.class).
//      filter(record -> record.getMessage().contains("HEADERS")).count());
//    assertEquals(2, factory.
//      logs(Http3FrameLogger.class).
//      filter(record -> record.getMessage().contains("DATA")).count());
//    factory.
//      logs(Http3FrameLogger.class).
//      filter(record -> record.getMessage().contains("DATA"))
//      .map(record -> record.getParameters()[4]).forEach(o -> {
//        assertEquals(ByteBufUtil.hexDump("Hello World".getBytes()), o);
//      });
//    assertEquals(1, factory.
//      logs(Http3FrameLogger.class).
//      filter(record -> record.getMessage().contains("SETTINGS")).count());
//    assertEquals(2, factory.
//      logs(Http3FrameLogger.class).
//      filter(record -> record.getMessage().contains("GO_AWAY")).count());
//  }
}
