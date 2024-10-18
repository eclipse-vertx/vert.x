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

package io.vertx.tests.net;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import io.vertx.core.net.*;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;

import static io.vertx.core.net.NetServerOptions.DEFAULT_PORT;

public class NetBandwidthLimitingTest extends VertxTestBase {

  public static final String DEFAULT_HOST = "localhost";
  private static final int OUTBOUND_LIMIT = 64 * 1024;  // 64KB/s
  private static final int INBOUND_LIMIT = 64 * 1024;   // 64KB/s

  @Rule
  public TemporaryFolder testFolder = new TemporaryFolder();

  private SocketAddress testAddress;
  private NetClient client = null;

  @Before
  public void setUp() throws Exception {
    super.setUp();
    if (USE_DOMAIN_SOCKETS) {
      assertTrue("Native transport not enabled", TRANSPORT.implementation().supportsDomainSockets());
      File tmp = TestUtils.tmpFile(".sock");
      testAddress = SocketAddress.domainSocketAddress(tmp.getAbsolutePath());
    } else {
      testAddress = SocketAddress.inetSocketAddress(1234, "localhost");
    }
    client = vertx.createNetClient();
  }

  @After
  public void after() throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(2);
    client.close().onComplete(v -> countDownLatch.countDown());
    vertx.close().onComplete(v -> countDownLatch.countDown());
    awaitLatch(countDownLatch);
  }

  @Test
  public void sendBufferThrottled() {
    long startTime = System.nanoTime();

    Buffer expected = TestUtils.randomBuffer(64 * 1024 * 4);
    Buffer received = Buffer.buffer();
    NetServer server = netServer(vertx);
    server.connectHandler(sock -> {
      sock.handler(buf -> {
        sock.write(expected);
      });
    });
    Future<NetServer> result = server.listen(testAddress);
    result.onComplete(onSuccess(resp -> {
      Future<NetSocket> clientConnect = client.connect(testAddress);
      clientConnect.onComplete(onSuccess(sock -> {
        sock.handler(buff -> {
          received.appendBuffer(buff);
          if (received.length() == expected.length()) {
            long expectedTimeInMillis = expectedTimeMillis(received.length(), OUTBOUND_LIMIT);
            assertEquals(expected, received);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            assertTimeTakenFallsInRange(expectedTimeInMillis, elapsedMillis);
            testComplete();
          }
        });
        sock.write("foo");
      }));
    }));
    await();
  }

  @Test
  public void sendFileIsThrottled() throws Exception {
    long startTime = System.nanoTime();

    File fDir = testFolder.newFolder();
    String content = TestUtils.randomUnicodeString(60000);
    File file = setupFile(fDir.toString(), "some-file.txt", content);
    Buffer expected = Buffer.buffer(content);
    Buffer received = Buffer.buffer();
    NetServer server = netServer(vertx);
    server.connectHandler(sock -> {
      sock.handler(buf -> {
        sock.sendFile(file.getAbsolutePath());
      });
    });
    Future<NetServer> result = server.listen(testAddress);
    result.onComplete(onSuccess(resp -> {
      Future<NetSocket> clientConnect = client.connect(testAddress);
      clientConnect.onComplete(onSuccess(sock -> {
        sock.handler(buff -> {
          received.appendBuffer(buff);
          if (received.length() == expected.length()) {
            long expectedTimeInMillis= expectedTimeMillis(received.length(), OUTBOUND_LIMIT);
            assertEquals(expected, received);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            assertTimeTakenFallsInRange(expectedTimeInMillis, elapsedMillis);
            testComplete();
          }
        });
        sock.write("foo");
      }));
    }));
    await();
  }

  @Test
  public void dataUploadIsThrottled() {
    long startTime = System.nanoTime();

    Buffer expected = TestUtils.randomBuffer(64 * 1024 * 4);
    Buffer received = Buffer.buffer();
    NetServer server = netServer(vertx);
    server.connectHandler(sock -> {
      sock.handler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == expected.length()) {
          long expectedTimeInMillis = expectedTimeMillis(received.length(), INBOUND_LIMIT);
          assertEquals(expected, received);
          long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
          assertTimeTakenFallsInRange(expectedTimeInMillis, elapsedMillis);
          testComplete();
        }
      });
      // Send some data to the client to trigger the buffer write
      sock.write("foo");
    });
    Future<NetServer> result = server.listen(testAddress);
    result.onComplete(onSuccess(resp -> {
      Future<NetSocket> clientConnect = client.connect(testAddress);
      clientConnect.onComplete(onSuccess(sock -> {
        sock.handler(buf -> {
          sock.write(expected);
        });
      }));
    }));
    await();
  }

  @Test
  public void fileUploadIsThrottled() throws Exception {
    long startTime = System.nanoTime();

    File fDir = testFolder.newFolder();
    String content = TestUtils.randomUnicodeString(60000);
    File file = setupFile(fDir.toString(), "some-file.txt", content);
    Buffer expected = Buffer.buffer(content);
    Buffer received = Buffer.buffer();
    NetServer server = netServer(vertx);
    server.connectHandler(sock -> {
      sock.handler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == expected.length()) {
          long expectedTimeInMillis = expectedTimeMillis(received.length(), INBOUND_LIMIT);
          assertEquals(expected, received);
          long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
          assertTimeTakenFallsInRange(expectedTimeInMillis, elapsedMillis);
          testComplete();
        }
      });
      // Send some data to the client to trigger the sendfile
      sock.write("foo");
    });
    Future<NetServer> result = server.listen(testAddress);
    result.onComplete(onSuccess(resp -> {
      Future<NetSocket> clientConnect = client.connect(testAddress);
      clientConnect.onComplete(onSuccess(sock -> {
        sock.handler(buf -> {
          sock.sendFile(file.getAbsolutePath());
        });
      }));
    }));
    await();
  }

  @Test
  public void testSendBufferIsTrafficShapedWithSharedServers() throws Exception {
    Buffer expected = TestUtils.randomBuffer(64 * 1024 * 4);

    int numEventLoops = 4; // We start a shared TCP server with 4 event-loops
    Future<String> listenLatch = vertx.deployVerticle(() -> new AbstractVerticle() {
      @Override
      public void start(Promise<Void> startPromise) {
        NetServer testServer = netServer(vertx);
        testServer.connectHandler(sock -> {
          sock.handler(buf -> {
            sock.write(expected);
          });
        });
        testServer.listen(testAddress).onComplete(v -> startPromise.complete());
      }
    }, new DeploymentOptions().setInstances(numEventLoops));

    AtomicLong startTime = new AtomicLong();
    CountDownLatch waitForResponse = new CountDownLatch(4);
    listenLatch.onComplete(onSuccess(v -> {
      startTime.set(System.nanoTime());
      for (int i=0; i<4; i++) {
        Buffer received = Buffer.buffer();
        Future<NetSocket> clientConnect = client.connect(testAddress);
        clientConnect.onComplete(onSuccess(sock -> {
          sock.handler(buff -> {
            received.appendBuffer(buff);
            if (received.length() == expected.length()) {
              assertEquals(expected, received);
              waitForResponse.countDown();
            }
          });
          sock.write("foo");
        }));
      }
    }));
    waitForResponse.await();
    long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime.get());
    long expectedTimeInMillis= expectedTimeMillis(expected.length() * 4, OUTBOUND_LIMIT); // 4 simultaneous requests
    assertTimeTakenFallsInRange(expectedTimeInMillis, elapsedMillis);
  }

  @Test
  public void testDynamicInboundRateUpdate() {
    long startTime = System.nanoTime();

    Buffer expected = TestUtils.randomBuffer(64 * 1024 * 4);
    Buffer received = Buffer.buffer();
    NetServer server = netServer(vertx);

    server.connectHandler(sock -> {
      sock.handler(buff -> {
        received.appendBuffer(buff);
        if (received.length() == expected.length()) {
          assertEquals(expected, received);
          long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
          assertTrue(elapsedMillis < expectedUpperBoundTimeMillis(received.length(), INBOUND_LIMIT));
          testComplete();
        }
      });
      // Send some data to the client to trigger the buffer write
      sock.write("foo");
    });
    Future<NetServer> result = server.listen(testAddress);

    // update rate
    TrafficShapingOptions trafficOptions = new TrafficShapingOptions()
                                             .setOutboundGlobalBandwidth(OUTBOUND_LIMIT) // unchanged
                                             .setInboundGlobalBandwidth(2 * INBOUND_LIMIT);
    server.updateTrafficShapingOptions(trafficOptions);

    result.onComplete(onSuccess(resp -> {
      Future<NetSocket> clientConnect = client.connect(testAddress);
      clientConnect.onComplete(onSuccess(sock -> {
        sock.handler(buf -> {
          sock.write(expected);
        });
      }));
    }));
    await();
  }

  @Test
  public void testDynamicOutboundRateUpdate() {
    long startTime = System.nanoTime();

    Buffer expected = TestUtils.randomBuffer(64 * 1024 * 4);
    Buffer received = Buffer.buffer();
    NetServer server = netServer(vertx);
    server.connectHandler(sock -> {
      sock.handler(buf -> {
        sock.write(expected);
      });
    });
    Future<NetServer> result = server.listen(testAddress);

    // update rate
    TrafficShapingOptions trafficOptions = new TrafficShapingOptions()
                                             .setInboundGlobalBandwidth(INBOUND_LIMIT) // unchanged
                                             .setOutboundGlobalBandwidth(2 * OUTBOUND_LIMIT);
    server.updateTrafficShapingOptions(trafficOptions);

    result.onComplete(onSuccess(resp -> {
      Future<NetSocket> clientConnect = client.connect(testAddress);
      clientConnect.onComplete(onSuccess(sock -> {
        sock.handler(buff -> {
          received.appendBuffer(buff);
          if (received.length() == expected.length()) {
            assertEquals(expected, received);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            assertTrue(elapsedMillis < expectedUpperBoundTimeMillis(received.length(), OUTBOUND_LIMIT));
            testComplete();
          }
        });
        sock.write("foo");
      }));
    }));
    await();
  }

  @Test(expected = IllegalStateException.class)
  public void testRateUpdateWhenServerStartedWithoutTrafficShaping() {
    NetServerOptions options = new NetServerOptions().setHost(DEFAULT_HOST).setPort(DEFAULT_PORT);
    NetServer testServer = vertx.createNetServer(options);

    // update inbound rate to twice the limit
    TrafficShapingOptions trafficOptions = new TrafficShapingOptions()
                                             .setOutboundGlobalBandwidth(OUTBOUND_LIMIT)
                                             .setInboundGlobalBandwidth(2 * INBOUND_LIMIT);
    testServer.updateTrafficShapingOptions(trafficOptions);
  }

  /**
   * Calculate time taken for transfer given bandwidth limit set.
   *
   * @param size total size of data transferred in bytes
   * @param rate set inbound or outbound bandwidth limit
   * @return expected time to be taken by server to send/receive data
   */
  private long expectedTimeMillis(int size, int rate) {
    return TimeUnit.MILLISECONDS.convert((size / rate), TimeUnit.SECONDS);
  }

  /**
   * Upperbound time taken is calculated with old rate limit before update. Hence time taken should be less than this value.
   */
  private long expectedUpperBoundTimeMillis(int size, int rate) {
    return TimeUnit.MILLISECONDS.convert((size / rate), TimeUnit.SECONDS);
  }

  /**
   * The throttling takes a while to kick in so the expected time cannot be strict especially
   * for small data sizes in these tests.
   */
  private void assertTimeTakenFallsInRange(long expectedTimeInMillis, long actualTimeInMillis)
  {
    Assert.assertTrue(actualTimeInMillis >= expectedTimeInMillis - 2000);
    Assert.assertTrue(actualTimeInMillis <= expectedTimeInMillis + 2000); // +/- 2000 millis considered to be tolerant of time pauses during CI runs
  }

  private NetServer netServer(Vertx vertx) {
    NetServerOptions options = new NetServerOptions()
                                  .setHost(DEFAULT_HOST)
                                  .setPort(DEFAULT_PORT)
                                  .setTrafficShapingOptions(new TrafficShapingOptions()
                                                              .setInboundGlobalBandwidth(NetBandwidthLimitingTest.INBOUND_LIMIT)
                                                              .setOutboundGlobalBandwidth(NetBandwidthLimitingTest.OUTBOUND_LIMIT));

    return vertx.createNetServer(options);
  }

  private File setupFile(String testDir, String fileName, String content) throws Exception {
    File file = new File(testDir, fileName);
    if (file.exists()) {
      file.delete();
    }
    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    out.write(content);
    out.close();
    return file;
  }
}
