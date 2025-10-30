package io.vertx.tests.http.http3;

import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.http3.DefaultHttp3Headers;
import io.netty.handler.codec.http3.Http3Headers;
import io.netty.util.NetUtil;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Http3ServerOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

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
  public void testServerConnectionShutdown() throws Exception{

    disableThreadChecks();
    waitFor(2);

    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      req.connection().shutdown(10, TimeUnit.SECONDS)
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
    connection.goAwayHandler(this::complete);
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    assertEquals("Hello World", new String(stream.responseBody()));

    await();
  }

  @Test
  public void testServerConnectionShutdownTimeout() throws Exception{

    disableThreadChecks();
    waitFor(2);

    server.requestHandler(req -> {
      long now = System.currentTimeMillis();
      req.connection().shutdown(1, TimeUnit.SECONDS)
        .onComplete(onSuccess2(v -> {
          assertTrue(System.currentTimeMillis() - now <= 2000);
          complete();
        }));
    });

    server.listen(8443, "localhost").await();

    Http3NettyTest.Client.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 8443));
    connection.goAwayHandler(this::complete);
    Http3NettyTest.Client.Stream stream = connection.stream();
    stream.GET("/");
    assertEquals("", new String(stream.responseBody()));

    await();
  }
}
