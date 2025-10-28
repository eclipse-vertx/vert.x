package io.vertx.tests.http.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.quic.QuicException;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.*;
import io.vertx.core.net.QuicClientAddressValidation;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(LinuxOrOsx.class)
public class Http3ServerTest extends VertxTestBase {

  private HttpServer server;
  private Http3NettyTest.Client client;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    client = Http3NettyTest.client(new NioEventLoopGroup(1));
    Http3ServerOptions options = new Http3ServerOptions();
    options.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
//    options.setClientAddressValidation(QuicClientAddressValidation.NONE);
//    options.setKeyLogFile("/Users/julien/keylogfile.txt");
    server = vertx.createHttpServer(options);
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
    stream.resetHandler(v -> {
      assertEquals(1, goAways.size());
    });
    stream.GET("/");
    try {
      stream.responseBody();
      fail();
    } catch (QuicException e) {
      assertEquals("STREAM_RESET", e.getMessage());
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
    } catch (QuicException e) {
      assertEquals("STREAM_RESET", e.getMessage());
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
          response.reset().onComplete(onSuccess(v2 -> testComplete()));
      }));
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    try {
      stream.responseBody();
      fail();
    } catch (QuicException expected) {
      assertEquals("STREAM_RESET", expected.getMessage());
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
    } catch (QuicException e) {
      assertEquals("STREAM_RESET", e.getMessage());
    }

    await();
  }
}
