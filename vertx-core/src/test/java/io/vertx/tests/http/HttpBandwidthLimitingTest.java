/*
 * Copyright (c) 2011-2021 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.http;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.*;
import io.vertx.core.http.*;
import io.vertx.core.net.TrafficShapingOptions;
import io.vertx.test.http.HttpTestBase;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;

@RunWith(Parameterized.class)
public class HttpBandwidthLimitingTest extends Http2TestBase {

  private static final int OUTBOUND_LIMIT = 64 * 1024;  // 64KB/s
  private static final int INBOUND_LIMIT = 64 * 1024;   // 64KB/s
  private static final int TEST_CONTENT_SIZE = 64 * 1024 * 4;   // 64 * 4 = 256KB

  protected HttpServerOptions serverOptions;
  protected HttpClientOptions clientOptions;
  protected List<EventLoopGroup> eventLoopGroups = new ArrayList<>();

  private final File sampleF = new File(new File(TestUtils.MAVEN_TARGET_DIR, "test-classes"), "test_traffic.txt");
  private final Handlers HANDLERS = new Handlers();

  @Parameters(name = "HTTP {0}")
  public static Iterable<Object[]> data() {

    Function<Vertx, HttpServer> http1ServerFactory = (v) -> Providers.http1Server(v, INBOUND_LIMIT, OUTBOUND_LIMIT);
    Function<Vertx, HttpServer> http2ServerFactory = (v) -> Providers.http2Server(v, INBOUND_LIMIT, OUTBOUND_LIMIT);
    Function<Vertx, HttpServer> http3ServerFactory = (v) -> Providers.http3Server(v, INBOUND_LIMIT, OUTBOUND_LIMIT);
    Function<Vertx, HttpServer> http1NonTrafficShapedServerFactory = (v) -> Providers.http1Server(v, 0, 0);
    Function<Vertx, HttpServer> http2NonTrafficShapedServerFactory = (v) -> Providers.http1Server(v, 0, 0);
    Function<Vertx, HttpServer> http3NonTrafficShapedServerFactory = (v) -> Providers.http1Server(v, 0, 0);
    Function<Vertx, HttpClient> http1ClientFactory = (v) -> v.createHttpClient();
    Function<Vertx, HttpClient> http2ClientFactory = (v) -> v.createHttpClient(Http2TestBase.createHttp2ClientOptions());
    Function<Vertx, HttpClient> http3ClientFactory = (v) -> v.createHttpClient(Http2TestBase.createH3HttpClientOptions());

    return Arrays.asList(new Object[][] {
      { 1.1, http1ServerFactory, http1ClientFactory, http1NonTrafficShapedServerFactory },
      { 2.0, http2ServerFactory, http2ClientFactory, http2NonTrafficShapedServerFactory },
      { 3.0, http3ServerFactory, http3ClientFactory, http3NonTrafficShapedServerFactory }
    });
  }

  protected Function<Vertx, HttpServer> serverFactory;
  protected Function<Vertx, HttpClientAgent> clientFactory;
  protected Function<Vertx, HttpServer> nonTrafficShapedServerFactory;

  public HttpBandwidthLimitingTest(double protoVersion, Function<Vertx, HttpServer> serverFactory,
                                   Function<Vertx, HttpClientAgent> clientFactory,
                                   Function<Vertx, HttpServer> nonTrafficShapedServerFactory) {
    this.serverFactory = serverFactory;
    this.clientFactory = clientFactory;
    this.nonTrafficShapedServerFactory = nonTrafficShapedServerFactory;
  }

  @Override
  protected void configureDomainSockets() throws Exception {
    // Nope
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    for (EventLoopGroup eventLoopGroup : eventLoopGroups) {
      eventLoopGroup.shutdownGracefully(0, 10, TimeUnit.SECONDS);
    }
  }

  @Override
  protected HttpServerOptions createBaseServerOptions() {
    return createHttp2ServerOptions(DEFAULT_HTTPS_PORT, DEFAULT_HTTPS_HOST);
  }

  @Override
  protected HttpClientOptions createBaseClientOptions() {
    return createHttp2ClientOptions();
  }


  @After
  public void after() throws InterruptedException
  {
    CountDownLatch waitForClose = new CountDownLatch(1);
    vertx.close().onComplete(onSuccess(resp -> waitForClose.countDown()));
    awaitLatch(waitForClose);
  }

  @Test
  public void sendBufferThrottled() throws Exception {
    Buffer expectedBuffer = TestUtils.randomBuffer(TEST_CONTENT_SIZE);

    HttpServer testServer = serverFactory.apply(vertx);
    testServer.requestHandler(HANDLERS.bufferRead(expectedBuffer));
    startServer(testServer);

    long startTime = System.nanoTime();
    HttpClient testClient = clientFactory.apply(vertx);
    read(expectedBuffer, testServer, testClient);
    await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

    Assert.assertTrue(elapsedMillis > expectedTimeMillis(TEST_CONTENT_SIZE, OUTBOUND_LIMIT));
  }

  @Test
  public void sendFileIsThrottled() throws Exception {
    HttpServer testServer = serverFactory.apply(vertx);
    testServer.requestHandler(HANDLERS.getFile(sampleF));
    startServer(testServer);

    long startTime = System.nanoTime();
    HttpClient testClient = clientFactory.apply(vertx);
    AtomicLong receivedLength = new AtomicLong();
    long expectedLength = Files.size(Path.of(sampleF.getAbsolutePath()));
    testClient.request(HttpMethod.GET, testServer.actualPort(), DEFAULT_HTTP_HOST,"/get-file")
              .compose(req -> req.send()
                .expecting(HttpResponseExpectation.SC_OK)
                .compose(HttpClientResponse::body))
              .onComplete(onSuccess(body -> {
                receivedLength.set(body.getBytes().length);
                Assert.assertEquals(expectedLength, receivedLength.get());
                testComplete();
              }));
    await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

    Assert.assertTrue(elapsedMillis > expectedTimeMillis(receivedLength.get(), OUTBOUND_LIMIT));
  }

  @Test
  public void dataUploadIsThrottled() throws Exception {
    Buffer expectedBuffer = TestUtils.randomBuffer((TEST_CONTENT_SIZE));

    HttpServer testServer = serverFactory.apply(vertx);
    testServer.requestHandler(HANDLERS.bufferWrite(expectedBuffer));
    startServer(testServer);

    long startTime = System.nanoTime();
    HttpClient testClient = clientFactory.apply(vertx);
    write(expectedBuffer, testServer, testClient);
    await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

    Assert.assertTrue(elapsedMillis > expectedTimeMillis(TEST_CONTENT_SIZE, INBOUND_LIMIT));
  }

  @Test
  public void fileUploadIsThrottled() throws Exception {
    HttpServer testServer = serverFactory.apply(vertx);
    testServer.requestHandler(HANDLERS.uploadFile(sampleF));
    startServer(testServer);

    long startTime = System.nanoTime();
    HttpClient testClient = clientFactory.apply(vertx);
    upload(testServer, testClient, sampleF);
    await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

    Assert.assertTrue( elapsedMillis >  expectedTimeMillis(Files.size(Path.of(sampleF.getAbsolutePath())), INBOUND_LIMIT));
  }

  @Test
  public void testSendFileTrafficShapedWithSharedServers() throws InterruptedException, IOException {
    int numEventLoops = 2; // We start a shared TCP server with 2 event-loops
    Future<String> listenLatch = vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        HttpServer testServer = serverFactory.apply(vertx);
        testServer.requestHandler(HANDLERS.getFile(sampleF))
                  .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST).<Void>mapEmpty().onComplete(startPromise);
      }
    }, new DeploymentOptions().setInstances(numEventLoops));

    HttpClient testClient = clientFactory.apply(vertx);
    CountDownLatch waitForResponse = new CountDownLatch(2);
    AtomicLong startTime = new AtomicLong();
    AtomicLong totalReceivedLength = new AtomicLong();
    long expectedLength = Files.size(Path.of(sampleF.getAbsolutePath()));
    listenLatch.onComplete(onSuccess(v -> {
      startTime.set(System.nanoTime());
      for (int i=0; i<2; i++) {
        testClient.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST,"/get-file")
                  .compose(req -> req.send()
                    .expecting(HttpResponseExpectation.SC_OK)
                    .compose(HttpClientResponse::body))
                  .onComplete(onSuccess(body -> {
                      long receivedBytes = body.getBytes().length;
                      totalReceivedLength.addAndGet(receivedBytes);
                      Assert.assertEquals(expectedLength, receivedBytes);
                      waitForResponse.countDown();
                  }));
      }
    }));
    awaitLatch(waitForResponse);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get());
    Assert.assertTrue(elapsedMillis > expectedTimeMillis(totalReceivedLength.get(), OUTBOUND_LIMIT)); // because there are simultaneous 2 requests
  }

  @Test
  public void testDynamicOutboundRateUpdateSharedServers() throws Exception {
    int numEventLoops = 5; // We start a shared TCP server with 5 event-loops
    List<HttpServer> servers = Collections.synchronizedList(new ArrayList<>());
    vertx.deployVerticle(() -> ctx -> {
        HttpServer testServer = serverFactory.apply(vertx);
        servers.add(testServer);
        return testServer
          .requestHandler(HANDLERS.getFile(sampleF))
          .listen(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);
      }, new DeploymentOptions().setInstances(numEventLoops))
      .await(20, TimeUnit.SECONDS);

    // Apply traffic shaping options after the server has started
    TrafficShapingOptions updatedTrafficOptions = new TrafficShapingOptions()
      .setInboundGlobalBandwidth(INBOUND_LIMIT)
      .setOutboundGlobalBandwidth(2 * OUTBOUND_LIMIT);

    List<Future<?>> promises;
    promises = servers
      .stream()
      .map(server -> server.updateTrafficShapingOptions(updatedTrafficOptions))
      .collect(Collectors.toList());
    // Ensure all traffic shaping updates complete before resolving the startPromise
    Future.all(promises).await(20, TimeUnit.SECONDS);

    HttpClient testClient = clientFactory.apply(vertx);
    CountDownLatch waitForResponse = new CountDownLatch(2);
    AtomicLong startTime = new AtomicLong();
    AtomicLong totalReceivedLength = new AtomicLong();
    long expectedLength = Files.size(Paths.get(sampleF.getAbsolutePath()));
    startTime.set(System.nanoTime());
    for (int i = 0; i < 2; i++) {
      testClient.request(HttpMethod.GET, DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST, "/get-file")
        .compose(req -> req.send()
          .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
          .compose(HttpClientResponse::body))
        .onComplete(onSuccess(body -> {
          long receivedBytes = body.getBytes().length;
          totalReceivedLength.addAndGet(receivedBytes);
          Assert.assertEquals(expectedLength, receivedBytes);
          waitForResponse.countDown();
        }));
    }
    awaitLatch(waitForResponse);
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get());
    Assert.assertTrue(elapsedMillis < expectedUpperBoundTimeMillis(totalReceivedLength.get(), OUTBOUND_LIMIT));
  }

  @Test
  public void testDynamicOutboundRateUpdate() throws Exception {
    Buffer expectedBuffer = TestUtils.randomBuffer(TEST_CONTENT_SIZE);

    HttpServer testServer = serverFactory.apply(vertx);
    testServer.requestHandler(HANDLERS.bufferRead(expectedBuffer));
    startServer(testServer);

    // update outbound rate to twice the limit
    TrafficShapingOptions trafficOptions = new TrafficShapingOptions()
                                             .setInboundGlobalBandwidth(INBOUND_LIMIT) // unchanged
                                             .setOutboundGlobalBandwidth(2 * OUTBOUND_LIMIT);
    testServer.updateTrafficShapingOptions(trafficOptions);

    long startTime = System.nanoTime();
    HttpClient testClient = clientFactory.apply(vertx);
    read(expectedBuffer, testServer, testClient);
    await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

    Assert.assertTrue(elapsedMillis < expectedUpperBoundTimeMillis(TEST_CONTENT_SIZE, OUTBOUND_LIMIT));
  }

  @Test
  public void testDynamicInboundRateUpdate() throws Exception {
    Buffer expectedBuffer = TestUtils.randomBuffer((TEST_CONTENT_SIZE));

    HttpServer testServer = serverFactory.apply(vertx);
    testServer.requestHandler(HANDLERS.bufferWrite(expectedBuffer));
    startServer(testServer);

    // update inbound rate to twice the limit
    TrafficShapingOptions trafficOptions = new TrafficShapingOptions()
                                             .setOutboundGlobalBandwidth(OUTBOUND_LIMIT) // unchanged
                                             .setInboundGlobalBandwidth(2 * INBOUND_LIMIT);
    testServer.updateTrafficShapingOptions(trafficOptions);

    long startTime = System.nanoTime();
    HttpClient testClient = clientFactory.apply(vertx);
    write(expectedBuffer, testServer, testClient);
    await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

    Assert.assertTrue(elapsedMillis < expectedUpperBoundTimeMillis(TEST_CONTENT_SIZE, INBOUND_LIMIT));
  }

  @Test(expected = IllegalStateException.class)
  public void testRateUpdateWhenServerStartedWithoutTrafficShaping() {
    HttpServer testServer = nonTrafficShapedServerFactory.apply(vertx);

    // update inbound rate to twice the limit
    TrafficShapingOptions trafficOptions = new TrafficShapingOptions()
                                             .setOutboundGlobalBandwidth(OUTBOUND_LIMIT)
                                             .setInboundGlobalBandwidth(2 * INBOUND_LIMIT);
    testServer.updateTrafficShapingOptions(trafficOptions);
  }

  /**
   * The throttling takes a while to kick in so the expected time cannot be strict especially
   * for small data sizes in these tests.
   *
   * @param size
   * @param rate
   * @return
   */
  private long expectedTimeMillis(long size, int rate) {
    return (long) (TimeUnit.MILLISECONDS.convert(( size / rate), TimeUnit.SECONDS) * 0.5); // multiplied by 0.5 to be more tolerant of time pauses during CI runs
  }

  private long expectedUpperBoundTimeMillis(long size, int rate) {
    return TimeUnit.MILLISECONDS.convert(( size / rate), TimeUnit.SECONDS); // Since existing rate will be upperbound, runs should complete by this time
  }

  private void read(Buffer expected, HttpServer server, HttpClient client) {
    client.request(HttpMethod.GET, server.actualPort(), DEFAULT_HTTP_HOST,"/buffer-read")
          .compose(req -> req
            .send()
            .expecting(HttpResponseExpectation.SC_OK)
            .compose(HttpClientResponse::body))
          .onComplete(onSuccess(body -> {
              assertEquals(expected.getBytes().length, body.getBytes().length);
              testComplete();
          }));
  }

  private void write(Buffer buffer, HttpServer server, HttpClient client) {
    client.request(HttpMethod.POST, server.actualPort(), DEFAULT_HTTP_HOST, "/buffer-write")
          .compose(req -> req.putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(buffer.length()))
                             .end(buffer));
  }

  private void upload(HttpServer server, HttpClient client, File expected) {
    Buffer b = vertx.fileSystem().readFileBlocking(expected.getAbsolutePath());
    client.request(HttpMethod.PUT, server.actualPort(), DEFAULT_HTTP_HOST, "/upload-file")
          .compose(req -> req.putHeader(HttpHeaderNames.CONTENT_LENGTH, String.valueOf(expected.length()))
                             .putHeader(HttpHeaderNames.CONTENT_TYPE, "application/binary")
                             .end(b));
  }

  class Handlers {
    public Handler<HttpServerRequest> bufferRead(Buffer expectedBuffer) {
      return (req) -> {
        req.response().setChunked(true);

        int start = 0;
        int size = expectedBuffer.length();
        int chunkSize = OUTBOUND_LIMIT / 2;
        while (size > 0) {
          int len = Math.min(chunkSize, size);
          req.response().write(expectedBuffer.getBuffer(start, start + len));
          start += len;
          size -= len;
        }
        req.response().end();
      };
    }

    public Handler<HttpServerRequest> getFile(File expected) {
      return req -> req.response().sendFile(expected.getAbsolutePath());
    }

    public Handler<HttpServerRequest> bufferWrite(Buffer expected) {
      return req -> {
        req.bodyHandler(buffer -> {
          assertEquals(expected, buffer);
          testComplete();
        });
      };
    }

    public Handler<HttpServerRequest> uploadFile(File expected) {
      return req -> {
        req.endHandler((r) -> {
          assertEquals(expected.length(), req.bytesRead());
          testComplete();
        });
      };
    }
  }

  static class Providers {
    private static HttpServer http1Server(Vertx vertx, int inboundLimit, int outboundLimit) {
      HttpServerOptions options = new HttpServerOptions()
                                    .setHost(DEFAULT_HTTP_HOST)
                                    .setPort(DEFAULT_HTTP_PORT);

      if (inboundLimit != 0 || outboundLimit != 0) {
        options.setTrafficShapingOptions(new TrafficShapingOptions()
                                           .setInboundGlobalBandwidth(inboundLimit)
                                           .setOutboundGlobalBandwidth(outboundLimit));
      }

      return vertx.createHttpServer(options);
    }

    private static HttpServer http2Server(Vertx vertx, int inboundLimit, int outboundLimit) {
      HttpServerOptions options = Http2TestBase.createHttp2ServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);

      if (inboundLimit != 0 || outboundLimit != 0) {
        options.setTrafficShapingOptions(new TrafficShapingOptions()
                                           .setInboundGlobalBandwidth(inboundLimit)
                                           .setOutboundGlobalBandwidth(outboundLimit));
      }

      return vertx.createHttpServer(options);
    }

    private static HttpServer http3Server(Vertx vertx, int inboundLimit, int outboundLimit) {
      HttpServerOptions options = HttpTestBase.createH3HttpServerOptions(DEFAULT_HTTP_PORT, DEFAULT_HTTP_HOST);

      if (inboundLimit != 0 || outboundLimit != 0) {
        options.setTrafficShapingOptions(new TrafficShapingOptions()
                                           .setInboundGlobalBandwidth(inboundLimit)
                                           .setOutboundGlobalBandwidth(outboundLimit));
      }

      return vertx.createHttpServer(options);
    }
  }
}
