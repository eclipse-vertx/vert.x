package io.vertx5;

import io.vertx.core.Context;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.future.PromiseInternal;
import io.vertx5.core.Vertx;
import io.vertx5.core.buffer.Buffer;
import io.vertx5.core.buffer.impl.BufferOwnershipStrategy;
import io.vertx5.core.http.HttpClient;
import io.vertx5.core.http.HttpServer;
import io.vertx5.core.net.NetClient;
import io.vertx5.core.net.NetServer;
import io.vertx5.test.TestResult;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(Parameterized.class)
public class VertxTest {

  @Parameterized.Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(new Object[][] {
      { BufferOwnershipStrategy.COPY_ON_TRANSFER }, { BufferOwnershipStrategy.COPY_ON_WRITE }, { BufferOwnershipStrategy.SHARED }
    });
  }

  private BufferOwnershipStrategy bufferStrategy;
  Vertx vertx = Vertx.vertx();

  public VertxTest(BufferOwnershipStrategy bufferStrategy) {
    this.bufferStrategy = bufferStrategy;
  }

  @Before
  public void before() {
    vertx = Vertx.vertx();
    BufferOwnershipStrategy.ownershipStrategy(bufferStrategy);
  }

  @After
  public void after() throws Exception {
    vertx.close();
    vertx = null;
    BufferOwnershipStrategy.ownershipStrategy(BufferOwnershipStrategy.COPY_ON_TRANSFER);
  }

  @Test
  public void testGetOrCreateContext() {
    TestResult test = new TestResult();
    Context context = vertx.getOrCreateContext();
    context.runOnContext(v -> {
      test.run(() -> {
        Assert.assertSame(context, vertx.getOrCreateContext());
        test.complete();
      });
    });
    test.await();
  }

  @Test
  public void testPromise() {
    TestResult test = new TestResult();
    ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
    PromiseInternal<String> promise = context.promise();
    String expected = "hello";
    test.assertSuccess(promise, s -> {
      Assert.assertEquals(expected, s);
      test.complete();
    });
    promise.complete(expected);
    test.await();
  }

  @Test
  public void testBindNetServer() throws Exception {
    TestResult test = new TestResult();
    TestResult connected = new TestResult();
    TestResult bind = new TestResult();
    NetServer netServer = vertx.createNetServer();
    netServer.connectHandler(so -> {
      connected.complete();
      so.closeHandler(v -> {
        test.complete();
      });
    });
    bind.assertSuccess(netServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    Socket so = new Socket("localhost", 1234);
    connected.await();
    so.close();
    test.await();
  }

  @Test
  public void testNetServerPingPong() throws Exception {
    TestResult connected = new TestResult();
    TestResult bind = new TestResult();
    NetServer netServer = vertx.createNetServer();
    netServer.connectHandler(so -> {
      connected.complete();
      so.handler(buff -> {
        so.write(buff);
      });
    });
    bind.assertSuccess(netServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    Socket so = new Socket("localhost", 1234);
    connected.await();
    OutputStream out = so.getOutputStream();
    out.write("ping".getBytes());
    InputStream in = so.getInputStream();
    byte[] received = new byte[4];
    Assert.assertEquals(4, in.read(received));
    Assert.assertEquals("ping", new String(received));
  }

  @Test
  public void testNetServerReadInProgressWriteBatching() throws Exception {
    TestResult connected = new TestResult();
    TestResult bind = new TestResult();
    NetServer netServer = vertx.createNetServer();
    netServer.connectHandler(so -> {
      connected.complete();
      so.handler(buff -> {
        for (int i = 0;i < 10;i++) {
          so.write(Buffer.buffer("" + i));
        }
      });
    });
    bind.assertSuccess(netServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    Socket so = new Socket("localhost", 1234);
    connected.await();
    OutputStream out = so.getOutputStream();
    out.write("ping".getBytes());
    InputStream in = so.getInputStream();
    byte[] received = new byte[10];
    Assert.assertEquals(10, in.read(received));
    Assert.assertEquals("0123456789", new String(received));
  }

  @Test
  public void testCloseVertx() throws Exception {
    Vertx vertx = Vertx.vertx();
    TestResult bind = new TestResult();
    NetServer netServer = vertx.createNetServer();
    netServer.connectHandler(so -> {
    });
    TestResult close = new TestResult();
    close.assertSuccess(netServer.closeFuture(), v -> close.complete());
    bind.assertSuccess(netServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    vertx.close();
    close.await();
  }

  @Test
  public void testNetClient() throws Exception {
    TestResult bind = new TestResult();
    NetServer netServer = vertx.createNetServer();
    netServer.connectHandler(so -> {
      so.handler(buff -> {
        so.write(buff);
      });
    });
    bind.assertSuccess(netServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    TestResult test = new TestResult();
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost")
      .onSuccess(so -> {
        so.write(Buffer.buffer("ping"));
        so.handler(buff -> {
          test.complete();
        });
      });
    test.await();
  }

  @Test
  public void testHttpServer() throws Exception {
    TestResult bind = new TestResult();
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.streamHandler(stream -> {
      stream.endHandler(v -> {
        stream.end(Buffer.buffer("Hello World"));
      });
    });
    bind.assertSuccess(httpServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    Socket so = new Socket("localhost", 1234);
    OutputStream out = so.getOutputStream();
    out.write((
      "GET / HTTP/1.1\r\n" +
      "content-length: 0\r\n" +
      "\r\n").getBytes());
    InputStream in = so.getInputStream();
    byte[] received = new byte[4];
    Assert.assertEquals(4, in.read(received));
    Assert.assertEquals("HTTP", new String(received));
  }

  @Test
  public void testHttpClient() throws Exception {
    TestResult bind = new TestResult();
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.streamHandler(stream -> {
      stream.endHandler(v -> {
        stream.end(Buffer.buffer("Hello World"));
      });
    });
    bind.assertSuccess(httpServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    TestResult test = new TestResult();
    HttpClient client = vertx.createHttpClient();
    test.assertSuccess(client.connect(1234, "localhost"), conn -> {
      for (int i = 0;i < 5;i++) {
        int val = i;
        test.assertSuccess(conn.createStream(), stream -> {
          stream.method("GET");
          stream.uri("/");
          stream.end(Buffer.buffer("Hello World"));
          test.assertSuccess(stream.response(), v1 -> {
            Assert.assertEquals(200, stream.statusCode());
            stream.endHandler(v2 -> {
              if (val == 4) {
                test.complete();
              }
            });
          });
        });
      }
    });
    test.await();
  }

  @Test
  public void testChunked() throws Exception {
    TestResult bind = new TestResult();
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.streamHandler(stream -> {
      for (int i = 0;i < 5;i++) {
        stream.write(Buffer.buffer("" + i));
      }
      stream.end();
    });
    bind.assertSuccess(httpServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    TestResult test = new TestResult();
    HttpClient client = vertx.createHttpClient();
    test.assertSuccess(client.connect(1234, "localhost"), conn -> {
      test.assertSuccess(conn.createStream(), stream -> {
        stream.method("GET");
        stream.uri("/");
        stream.end(Buffer.buffer("Hello World"));
        test.assertSuccess(stream.response(), v1 -> {
          Assert.assertEquals(200, stream.statusCode());
          stream.handler(chunk -> {

          });
          stream.endHandler(v2 -> {
            test.complete();
          });
        });
      });
    });
    test.await();
  }

  @Test
  public void testBufferOwnership() throws Exception {
    Buffer buffer = Buffer.buffer("Hello World");
    TestResult bind = new TestResult();
    HttpServer httpServer = vertx.createHttpServer();
    httpServer.streamHandler(stream -> {
      stream.end(buffer);
    });
    bind.assertSuccess(httpServer.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    TestResult test = new TestResult();
    HttpClient client = vertx.createHttpClient();
    test.assertSuccess(client.connect(1234, "localhost"), conn -> {
      for (int i = 0;i < 2;i++) {
        int val = i;
        test.assertSuccess(conn.createStream(), stream -> {
          stream.method("GET");
          stream.uri("/");
          stream.end();
          test.assertSuccess(stream.response(), v1 -> {
            Assert.assertEquals(200, stream.statusCode());
            stream.handler(test.check(chunk -> {
              Assert.assertEquals("Hello World", chunk.toString());
            }));
            if (val == 1) {
              stream.endHandler(v2 -> {
                test.complete();
              });
            }
          });
        });
      }
    });
    test.await();
  }

  @Test
  public void testDelayFlush() throws Exception {
    Buffer buffer = Buffer.buffer(new byte[1024]);
    TestResult bind = new TestResult();
    CompletableFuture<Integer> readLatch = new CompletableFuture<>();
    NetServer server = vertx.createNetServer();
    server.connectHandler(stream -> {
      int extra = 16;
      int written = 0;
      while (true) {
        if (!stream.channelHandlerContext().channel().isWritable()) {
          if (extra == 0) {
            break;
          }
          extra--;
        }
        stream.write(buffer);
        written++;
      }
      readLatch.complete(written * 1024);
    });
    bind.assertSuccess(server.listen(1234, "localhost"), addr -> {
      bind.complete();
    });
    bind.await();
    Socket so = new Socket("localhost", 1234);
    OutputStream out = so.getOutputStream();
    InputStream in = so.getInputStream();
    int toRead = readLatch.get(20, TimeUnit.SECONDS);
    int read = 0;
    byte[] buff = new byte[1024];
    int amount = 0;
    while ((amount = in.read(buff)) != -1) {
      read += amount;
      if (read == toRead) {
        break;
      }
    }
  }
}
