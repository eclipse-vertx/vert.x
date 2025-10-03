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

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.handler.codec.quic.QuicClosedChannelException;
import io.netty.util.NetUtil;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.net.QuicConnectionClose;
import io.vertx.core.net.QuicServer;
import io.vertx.core.net.QuicServerOptions;
import io.vertx.test.core.LinuxOrOsx;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.tls.Cert;
import io.vertx.tests.net.QuicNettyTest;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.net.ssl.KeyManagerFactory;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@RunWith(LinuxOrOsx.class)
public class QuicServerTest extends VertxTestBase {

  private PfxOptions macKey;

  static QuicServerOptions serverOptions() {
    QuicServerOptions options = new QuicServerOptions();
    options.getSslOptions().setKeyCertOptions(Cert.SERVER_JKS.get());
    options.getSslOptions().setApplicationLayerProtocols(List.of("test-protocol"));
    options.getTransportOptions().setInitialMaxData(10000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalLocal(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataBidirectionalRemote(1000000L);
    options.getTransportOptions().setInitialMaxStreamDataUnidirectional(1000000L);
    options.getTransportOptions().setInitialMaxStreamsBidirectional(100L);
    options.getTransportOptions().setInitialMaxStreamsUnidirectional(100L);
    options.getTransportOptions().setActiveMigration(true);
    return options;
  }

  @Override
  public void setUp() throws Exception {

    KeyGenerator keygen = KeyGenerator.getInstance("HmacSHA256");
    SecretKey key = keygen.generateKey();
    KeyStore keystore = KeyStore.getInstance("pkcs12");
    keystore.load(null, null);
    keystore.setKeyEntry("theKey", key, "secret".toCharArray(), null);
    Path keystorePath = Files.createTempFile("keystore", ".p12");
    try (OutputStream out = new FileOutputStream(keystorePath.toFile())) {
      keystore.store(out, "secret".toCharArray());
    }
    macKey = new PfxOptions().setPath(keystorePath.toFile().getAbsolutePath()).setPassword("secret");

    super.setUp();
  }

  @Test
  public void testBind() {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    server.close().await();
  }

  @Test
  public void testConnect() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      assertEquals("test-protocol", conn.applicationLayerProtocol());

      conn.streamHandler(stream -> {
        stream.handler(buff -> {
          stream.write(buff);
        });
        stream.endHandler(v -> {
          stream.end();
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      List<String> received = Collections.synchronizedList(new ArrayList<>());
      stream.handler(data -> {
        received.add(new String(data, StandardCharsets.UTF_8));
      });
      stream.create();
      stream.write("Hello");
      assertWaitUntil(() -> received.equals(List.of("Hello")));
      stream.close();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testClientConnectionClose() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    AtomicInteger inflights = new AtomicInteger();
    server.handler(conn -> {
      inflights.incrementAndGet();
      conn.closeHandler(v -> {
        assertEquals(3, conn.closePayload().getError());
        assertEquals("done", conn.closePayload().getReason().toString());
        inflights.decrementAndGet();
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      assertWaitUntil(() -> inflights.get() == 1);
      connection.close(3, "done".getBytes(StandardCharsets.UTF_8));
      assertWaitUntil(() -> inflights.get() == 0);
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerConnectionClose() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> {
          vertx.setTimer(1, id -> {
            conn.close(new QuicConnectionClose().setError(3).setReason(Buffer.buffer("done")));
          });
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      CountDownLatch closeLatch = new CountDownLatch(1);
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      connection.closeHandler(v -> closeLatch.countDown());
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      List<String> received = Collections.synchronizedList(new ArrayList<>());
      stream.handler(data -> {
        received.add(new String(data, StandardCharsets.UTF_8));
      });
      stream.create();
      stream.write("ping");
      assertTrue(closeLatch.await(10, TimeUnit.SECONDS));
      assertEquals(3, connection.closeError());
      assertEquals("done", new String(connection.closeReason()));
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerCreatesStream() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.createStream().onComplete(onSuccess2(stream -> {
        stream.write("ping");
        stream.handler(buff -> {
          assertEquals("pong", buff.toString());
          testComplete();
        });
      }));
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connection();
      connection.handler(stream -> {
        stream.write("pong");
      });
      connection.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerCreatesUniStream() throws Exception {
    disableThreadChecks();
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.createStream(false).onComplete(onSuccess2(stream -> {
        assertFalse(stream.isBidirectional());
        assertTrue(stream.isLocalCreated());
        stream.write("ping");
      }));
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connection();
      connection.handler(stream -> {
        stream.handler(data -> {
          assertEquals("ping", new String(data, StandardCharsets.UTF_8));
          testComplete();
        });
      });
      connection.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testClientCreatesUniStream() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        assertFalse(stream.isBidirectional());
        assertFalse(stream.isLocalCreated());
        Future<Void> f = stream.write("pong");
        f.onComplete(onFailure(err -> {
          testComplete();
        }));
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      connection.newStream().create(false).write("ping");
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testShutdownConnection() throws Exception {
    disableThreadChecks();
    int numStreams = 5;
    waitFor(1 + numStreams);
    QuicServer server = QuicServer.create(vertx, serverOptions());
    AtomicInteger count = new AtomicInteger();
    server.handler(conn -> {
      AtomicInteger shutdown = new AtomicInteger();
      conn.streamHandler(stream -> {
        stream.shutdownHandler(v -> {
          shutdown.incrementAndGet();
          vertx.setTimer(10, id -> {
            stream.close();
          });
        });
        if (count.incrementAndGet() == numStreams) {
          Future<Void> res = conn.shutdown(Duration.ofSeconds(10));
          res.onComplete(onSuccess2(v -> {
            assertEquals(numStreams, shutdown.get());
            complete();
          }));
        }
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      List<QuicNettyTest.TestClient.Stream> streams = new ArrayList<>();
      for (int i = 0;i < numStreams;i++) {
        QuicNettyTest.TestClient.Stream stream = connection.newStream();
        streams.add(stream);
        stream.closeHandler(() -> {
          complete();
        });
        stream.create();
        stream.write("ping");
      }
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testShutdownServer() throws Exception {
    disableThreadChecks();
    int numStreams = 5;
    int numConnections = 2;
    waitFor(numConnections * numStreams);
    QuicServer server = QuicServer.create(vertx, serverOptions());
    AtomicInteger shutdown = new AtomicInteger();
    AtomicInteger count = new AtomicInteger();
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        count.incrementAndGet();
        stream.shutdownHandler(v -> {
          shutdown.incrementAndGet();
          vertx.setTimer(10, id -> {
            stream.close();
          });
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      List<QuicNettyTest.TestClient.Connection> connections = new ArrayList<>();
      for (int  j = 0;j < numConnections;j++) {
        QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
        connections.add(connection);
        List<QuicNettyTest.TestClient.Stream> streams = new ArrayList<>();
        for (int i = 0;i < numStreams;i++) {
          QuicNettyTest.TestClient.Stream stream = connection.newStream();
          streams.add(stream);
          stream.closeHandler(() -> {
            complete();
          });
          stream.create();
          stream.write("ping");
        }
      }
      assertWaitUntil(() -> count.get() == numConnections * numStreams);
      server.shutdown(Duration.ofSeconds(10)).await();
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testServerReset() throws Exception {
    disableThreadChecks();
    waitFor(2);
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> {
          stream.reset(0).onComplete(onSuccess2(v -> complete()));
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      stream.resetHandler(() -> complete());
      stream.create();
      stream.write("ping");
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testClientReset() throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions());
    server.handler(conn -> {
      conn.streamHandler(stream -> {
        stream.handler(buff -> {
          stream.resetHandler(reset -> {
            testComplete();
          });
        });
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      stream.create();
      stream.write("ping");
      stream.reset(4);
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testSendDatagram() throws Exception {
    disableThreadChecks();
    waitFor(2);
    QuicServerOptions options = serverOptions();
    options.getTransportOptions().setEnableDatagrams(true);
    QuicServer server = QuicServer.create(vertx, options);
    server.handler(conn -> {
      conn.writeDatagram(Buffer.buffer("ping")).onComplete(onSuccess2(v -> {
        complete();
      }));
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      client.connection().datagramHandler(datagram -> {
        assertEquals("ping", new String(datagram));
        complete();
      }).connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testReceiveDatagram() throws Exception {
    QuicServerOptions options = serverOptions();
    options.getTransportOptions().setEnableDatagrams(true);
    QuicServer server = QuicServer.create(vertx, options);
    server.handler(conn -> {
      conn.datagramHandler(dgram -> {
        assertEquals("ping", dgram.toString());
        testComplete();
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      connection.writeDatagram("ping".getBytes(StandardCharsets.UTF_8));
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Ignore("Needs to add channel handler that drop packets")
  @Test
  public void testMaxIdleTimeout() throws Exception {
    QuicServerOptions options = serverOptions();
    options.getTransportOptions().setMaxIdleTimeout(Duration.ofMillis(100));
    QuicServer server = QuicServer.create(vertx, options);
    server.handler(conn -> {
      System.out.println("connected");
      long now = System.currentTimeMillis();
      conn.closeHandler(v -> {
        System.out.println("closed " + (System.currentTimeMillis() - now));
      });
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    try {
      client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      QuicNettyTest.TestClient.Stream stream = connection.newStream();
      stream.create();
      await();
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testInvalidPrivateKey() {
    QuicServerOptions options = serverOptions()
      .setValidateClientAddress(true)
      .setClientAddressValidationKey(macKey.copy().setPassword("incorrect"))
      .setClientAddressValidationTimeWindow(Duration.ofSeconds(30));
    QuicServer server = QuicServer.create(vertx, options);
    server.handler(conn -> {
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost"))
      .onComplete(onFailure2(err -> {
        assertTrue(err.getCause() instanceof java.security.UnrecoverableKeyException);
      testComplete();
    }));
    await();
  }

  @Test
  public void testInvalidKeyConf() {
    QuicServerOptions options = serverOptions()
      .setValidateClientAddress(true)
      .setClientAddressValidationKey(new KeyCertOptions() {
      @Override
      public KeyCertOptions copy() {
        return this;
      }

      @Override
      public KeyManagerFactory getKeyManagerFactory(Vertx vertx) throws Exception {
        return null;
      }

      @Override
      public Function<String, KeyManagerFactory> keyManagerFactoryMapper(Vertx vertx) throws Exception {
        return null;
      }
    })
      .setClientAddressValidationTimeWindow(Duration.ofSeconds(30));
    QuicServer server = QuicServer.create(vertx, options);
    server.handler(conn -> {
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost"))
      .onComplete(onFailure2(err -> {
        assertTrue(err instanceof IllegalArgumentException);
        testComplete();
      }));
    await();
  }

  @Test
  public void testInvalidTokenTimeWindow() {
    try {
      new QuicServerOptions().setClientAddressValidationTimeWindow(null);
      fail();
    } catch (NullPointerException ignore) {
    }
    try {
      new QuicServerOptions().setClientAddressValidationTimeWindow(Duration.ofMillis(-10));
      fail();
    } catch (IllegalArgumentException ignore) {
    }
  }

  @Test
  public void testValidateTokenWithMac() throws Exception {
    testValidateToken(macKey);
  }

  @Test
  public void testValidateTokenWithJksDigitalSignature() throws Exception {
    testValidateToken(Cert.SERVER_JKS.get());
  }

  @Test
  public void testValidateTokenWithPemDigitalSignature() throws Exception {
    testValidateToken(Cert.SERVER_PEM.get());
  }

  private void testValidateToken(KeyCertOptions key) throws Exception {
    QuicServer server = QuicServer.create(vertx, serverOptions().setClientAddressValidationKey(key).setClientAddressValidationTimeWindow(Duration.ofSeconds(30)));
    AtomicInteger connections = new AtomicInteger();
    server.handler(conn -> {
      connections.incrementAndGet();
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    AtomicReference<byte[]> tokenRef = new AtomicReference<>();
    client.tokenHandler((token) -> tokenRef.set(ByteBufUtil.getBytes(token)));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      connection.close();
      assertNotNull(tokenRef.get());
      assertEquals(1, connections.get());
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testTokenExpiration() throws Exception {
    QuicServerOptions options = serverOptions()
      .setValidateClientAddress(true)
      .setClientAddressValidationKey(macKey)
      .setClientAddressValidationTimeWindow(Duration.ofMillis(10));
    QuicServer server = QuicServer.create(vertx, options);
    AtomicInteger connections = new AtomicInteger();
    server.handler(conn -> {
      connections.incrementAndGet();
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    AtomicBoolean executed = new AtomicBoolean();
    client.tokenHandler((token) -> {
      if (executed.compareAndSet(false, true)) {
        try {
          Thread.sleep(20);
        } catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    });
    try {
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      fail();
    } catch (QuicClosedChannelException ignore) {
      assertEquals(0, connections.get());
    } finally {
      client.close();
      server.close().await();
    }
  }

  @Test
  public void testInvalidToken() throws Exception {
    QuicServerOptions options = serverOptions()
      .setValidateClientAddress(true)
      .setClientAddressValidationKey(macKey)
      .setClientAddressValidationTimeWindow(Duration.ofSeconds(30));
    QuicServer server = QuicServer.create(vertx, options);
    AtomicInteger connections = new AtomicInteger();
    server.handler(conn -> {
      connections.incrementAndGet();
    });
    server.bind(SocketAddress.inetSocketAddress(9999, "localhost")).await();
    QuicNettyTest.TestClient client = new QuicNettyTest.TestClient(new NioEventLoopGroup(1));
    client.tokenHandler((token) -> token.setLong(0, 0L));
    try {
      QuicNettyTest.TestClient.Connection connection = client.connect(new InetSocketAddress(NetUtil.LOCALHOST4, 9999));
      fail();
    } catch (QuicClosedChannelException ignore) {
      assertEquals(0, connections.get());
    } finally {
      client.close();
      server.close().await();
    }
  }

  /*  @Test
  public void testSoReuse() throws Exception {

    InetSocketAddress addr = new InetSocketAddress("localhost", 4000);

    int num = 2;
    List<AtomicInteger> received = new ArrayList<>();
    for (int i = 0;i < num;i++) {
      AtomicInteger r = new AtomicInteger();
      received.add(r);
      new Thread(() -> {
        try {
          byte[] buff = new byte[65535];
          DatagramSocket socket = new DatagramSocket(null);
          socket.setOption(StandardSocketOptions.SO_REUSEPORT, true);
          socket.bind(addr);
          while (true) {
            DatagramPacket packet = new DatagramPacket(buff, buff.length);
            socket.receive(packet);
            r.incrementAndGet();
          }
        } catch (Exception e) {
          e.printStackTrace(System.out);
        }
      }).start();
    }

    int count = 1;
    AtomicInteger received1 = received.get(0);
    AtomicInteger received2 = received.get(1);
    final CountDownLatch latch = new CountDownLatch(count);
    AtomicInteger sent = new AtomicInteger();
    Runnable r = () -> {
      try {
        DatagramSocket socket = new DatagramSocket();
        while (received1.get() == 0 || received2.get() == 0) {
          final byte[] bytes = "data".getBytes();
          socket.send(new java.net.DatagramPacket(bytes, 0, bytes.length, addr.getAddress(), addr.getPort()));
          Thread.sleep(1);
        }
        socket.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
      latch.countDown();
    };

    ExecutorService executor = Executors.newFixedThreadPool(count);
    for (int i = 0 ; i < count; i++) {
      executor.execute(r);
    }

    latch.await();
    executor.shutdown();

    System.out.println(received1.get());
    System.out.println(received2.get());
    System.out.println(sent.get());
  }*/
}
