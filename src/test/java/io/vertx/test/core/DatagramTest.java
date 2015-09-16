/*
 * Copyright 2014 Red Hat, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetworkOptions;
import io.vertx.core.streams.WriteStream;
import org.junit.Test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DatagramTest extends VertxTestBase {

  private volatile DatagramSocket peer1;
  private volatile DatagramSocket peer2;

  protected void tearDown() throws Exception {
    if (peer1 != null) {
      CountDownLatch latch = new CountDownLatch(2);
      peer1.close(ar -> {
        assertTrue(ar.succeeded());
        latch.countDown();
        if (peer2 != null) {
          peer2.close(ar2 -> {
            assertTrue(ar2.succeeded());
            latch.countDown();
          });
        } else {
          latch.countDown();
        }
      });
      latch.await(10L, TimeUnit.SECONDS);
    }
    super.tearDown();
  }

  @Test
  public void testDatagramSocket() throws Exception {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());

    assertNullPointerException(() -> peer1.send((Buffer) null, 1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.send(Buffer.buffer(), -1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.send(Buffer.buffer(), 65536, "127.0.0.1", ar -> {}));

    assertNullPointerException(() -> peer1.send((String) null, 1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.send("", -1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.send("", 65536, "127.0.0.1", ar -> {}));

    assertNullPointerException(() -> peer1.send((String) null, "UTF-8", 1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.send("", "UTF-8", -1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.send("", "UTF-8", 65536, "127.0.0.1", ar -> {}));
    assertNullPointerException(() -> peer1.send("", null, 1, "127.0.0.1", ar -> {}));

    assertIllegalArgumentException(() -> peer1.sender(-1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.sender(65536, "127.0.0.1"));
    assertNullPointerException(() -> peer1.sender(1, null));

    assertIllegalArgumentException(() -> peer1.listen(-1, "127.0.0.1", ar -> {}));
    assertIllegalArgumentException(() -> peer1.listen(65536, "127.0.0.1", ar -> {}));
    assertNullPointerException(() -> peer1.listen(1, null, ar -> {}));
    assertNullPointerException(() -> peer1.listen(1, "127.0.0.1", null));
  }

  @Test
  public void testSendReceive() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      Buffer buffer = TestUtils.randomBuffer(128);
      peer2.handler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, 1234, "127.0.0.1", ar2 -> assertTrue(ar2.succeeded()));
    });
    await();
  }

  @Test
  public void testSendReceiveLargePacket() {
    int packetSize = 10000;
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions().setSendBufferSize(packetSize));
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions().setReceiveBufferSize(packetSize + 16)); // OSX needs 16 more
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      Buffer buffer = TestUtils.randomBuffer(packetSize);
      peer2.handler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, 1234, "127.0.0.1", ar2 -> assertTrue(ar2.succeeded()));
    });
    await();
  }

  @Test
  public void testEndHandler() {
    ThreadLocal<Object> stack = new ThreadLocal<>();
    stack.set(true);
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      peer2.endHandler(v -> {
        assertTrue(Vertx.currentContext().isEventLoopContext());
        assertNull(stack.get());
        testComplete();
      });
      peer2.close();
    });
    await();
  }

  @Test
  public void testPauseResume() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1", ar -> {
      Buffer buffer = TestUtils.randomBuffer(128);
      AtomicBoolean received = new AtomicBoolean();
      peer2.handler(packet -> received.set(true));
      peer2.pause();
      peer1.send(buffer, 1234, "127.0.0.1", ar2 -> {
        assertTrue(ar2.succeeded());
      });
      vertx.setTimer(100, l -> {
        peer2.handler(packet -> {
          assertFalse(received.get());
          assertEquals(buffer, packet.data());
          testComplete();
        });
        peer2.resume();
        peer1.send(buffer, 1234, "127.0.0.1", ar2 -> {
          assertTrue(ar2.succeeded());
        });
      });
    });
    await();
  }

  @Test
  public void testSender() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1", ar -> {
      Buffer buffer = TestUtils.randomBuffer(128);
      peer2.handler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });

      WriteStream<Buffer> sender1 = peer1.sender(1234, "127.0.0.1");
      sender1.write(buffer);
    });
  }

  @Test
  public void testListenHostPort() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenPort() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.listen(1234, "localhost", ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInetSocketAddress() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenSamePortMultipleTimes() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.listen(1234, "127.0.0.1", ar1 -> {
      assertTrue(ar1.succeeded());
      peer1.listen(1234, "127.0.0.1", ar2 -> {
        assertTrue(ar2.failed());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testEcho() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.exceptionHandler(t -> fail(t.getMessage()));
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      Buffer buffer = TestUtils.randomBuffer(128);
      peer2.handler(packet -> {
        assertEquals("127.0.0.1", packet.sender().host());
        assertEquals(1235, packet.sender().port());
        assertEquals(buffer, packet.data());
        peer2.send(packet.data(), 1235, "127.0.0.1", ar2 -> assertTrue(ar2.succeeded()));
      });
      peer1.listen(1235, "127.0.0.1", ar2 -> {
        peer1.handler(packet -> {
          assertEquals(buffer, packet.data());
          assertEquals("127.0.0.1", packet.sender().host());
          assertEquals(1234, packet.sender().port());
          testComplete();
        });
        peer1.send(buffer, 1234, "127.0.0.1", ar3 -> assertTrue(ar3.succeeded()));
      });
    });
    await();
  }

  @Test
  public void testSendAfterCloseFails() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.close(ar -> {
      assertTrue(ar.succeeded());
      peer1.send("Test", 1234, "127.0.0.1", ar2 -> {
        assertTrue(ar2.failed());
        peer1 = null;
        peer2.close(ar3 -> {
          assertTrue(ar3.succeeded());
          peer2.send("Test", 1234, "127.0.0.1", ar4 -> {
            assertTrue(ar4.failed());
            peer2 = null;
            testComplete();
          });
        });
      });
    });
    await();
  }

  @Test
  public void testBroadcast() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions().setBroadcast(true));
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions().setBroadcast(true));
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "0.0.0.0", ar1 -> {
      assertTrue(ar1.succeeded());
      Buffer buffer = TestUtils.randomBuffer(128);
      peer2.handler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, 1234, "255.255.255.255", ar2 -> {
        assertTrue(ar2.succeeded());
      });
    });
    await();
  }

  @Test
  public void testBroadcastFailsIfNotConfigured() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.send("test", 1234, "255.255.255.255", ar -> {
      assertTrue(ar.failed());
      testComplete();
    });
    await();
  }

  @Test
  public void testPause() {

  }

  @Test
  public void testMulticastJoinLeave() throws Exception {
    Buffer buffer = TestUtils.randomBuffer(128);
    String groupAddress = "230.0.0.1";
    String iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1")).getName();
    AtomicBoolean received = new AtomicBoolean();
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions().setMulticastNetworkInterface(iface));
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions().setMulticastNetworkInterface(iface));

    peer1.handler(packet -> {
      assertEquals(buffer, packet.data());
      received.set(true);
    });

    peer1.listen(1234, "0.0.0.0", ar1 -> {
      assertTrue(ar1.succeeded());
      peer1.listenMulticastGroup(groupAddress, iface, null, ar2 -> {
        assertTrue(ar2.succeeded());
        peer2.send(buffer, 1234, groupAddress, ar3 -> {
          assertTrue(ar3.succeeded());
          // leave group in 1 second so give it enough time to really receive the packet first
          vertx.setTimer(1000, id -> {
            peer1.unlistenMulticastGroup(groupAddress, iface, null, ar4 -> {
              assertTrue(ar4.succeeded());
              AtomicBoolean receivedAfter = new AtomicBoolean();
              peer1.handler(packet -> {
                // Should not receive any more event as it left the group
                receivedAfter.set(true);
              });
              peer2.send(buffer, 1234, groupAddress, ar5 -> {
                assertTrue(ar5.succeeded());
                // schedule a timer which will check in 1 second if we received a message after the group
                // was left before
                vertx.setTimer(1000, id2 -> {
                  assertFalse(receivedAfter.get());
                  assertTrue(received.get());
                  testComplete();
                });
              });
            });
          });
        });
      });
    });
    await();
  }

  @Test
  public void testOptions() {
    DatagramSocketOptions options = new DatagramSocketOptions();
    assertEquals(NetworkOptions.DEFAULT_SEND_BUFFER_SIZE, options.getSendBufferSize());
    int rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setSendBufferSize(rand));
    assertEquals(rand, options.getSendBufferSize());
    assertIllegalArgumentException(() -> options.setSendBufferSize(0));
    assertIllegalArgumentException(() -> options.setSendBufferSize(-123));

    assertEquals(NetworkOptions.DEFAULT_RECEIVE_BUFFER_SIZE, options.getReceiveBufferSize());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setReceiveBufferSize(rand));
    assertEquals(rand, options.getReceiveBufferSize());
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(0));
    assertIllegalArgumentException(() -> options.setReceiveBufferSize(-123));

    assertFalse(options.isReuseAddress());
    assertEquals(options, options.setReuseAddress(true));
    assertTrue(options.isReuseAddress());

    assertEquals(NetworkOptions.DEFAULT_TRAFFIC_CLASS, options.getTrafficClass());
    rand = 23;
    assertEquals(options, options.setTrafficClass(rand));
    assertEquals(rand, options.getTrafficClass());
    assertIllegalArgumentException(() -> options.setTrafficClass(-1));
    assertIllegalArgumentException(() -> options.setTrafficClass(256));

    assertFalse(options.isBroadcast());
    assertEquals(options, options.setBroadcast(true));
    assertTrue(options.isBroadcast());

    assertTrue(options.isLoopbackModeDisabled());
    assertEquals(options, options.setLoopbackModeDisabled(false));
    assertFalse(options.isLoopbackModeDisabled());

    assertEquals(-1, options.getMulticastTimeToLive());
    rand = TestUtils.randomPositiveInt();
    assertEquals(options, options.setMulticastTimeToLive(rand));
    assertEquals(rand, options.getMulticastTimeToLive());
    assertIllegalArgumentException(() -> options.setMulticastTimeToLive(-1));

    assertNull(options.getMulticastNetworkInterface());
    String randString = TestUtils.randomUnicodeString(100);
    assertEquals(options, options.setMulticastNetworkInterface(randString));
    assertEquals(randString, options.getMulticastNetworkInterface());

    assertFalse(options.isIpV6());
    assertEquals(options, options.setIpV6(true));
    assertTrue(options.isIpV6());

    testComplete();
  }

  @Test
  public void testCopyOptions() {
    DatagramSocketOptions options = new DatagramSocketOptions();
    Random rand = new Random();
    boolean broadcast = rand.nextBoolean();
    boolean loopbackModeDisabled = rand.nextBoolean();
    int multicastTimeToLive = TestUtils.randomPositiveInt();
    String multicastNetworkInterface = TestUtils.randomAlphaString(100);
    boolean reuseAddress = rand.nextBoolean();
    boolean ipV6 = rand.nextBoolean();
    options.setBroadcast(broadcast);
    options.setLoopbackModeDisabled(loopbackModeDisabled);
    options.setMulticastTimeToLive(multicastTimeToLive);
    options.setMulticastNetworkInterface(multicastNetworkInterface);
    options.setReuseAddress(reuseAddress);
    options.setIpV6(ipV6);
    DatagramSocketOptions copy = new DatagramSocketOptions(options);
    assertEquals(broadcast, copy.isBroadcast());
    assertEquals(loopbackModeDisabled, copy.isLoopbackModeDisabled());
    assertEquals(multicastTimeToLive, copy.getMulticastTimeToLive());
    assertEquals(multicastNetworkInterface, copy.getMulticastNetworkInterface());
    assertEquals(reuseAddress, copy.isReuseAddress());
    assertEquals(ipV6, copy.isIpV6());
    testComplete();
  }

  @Test
  public void testDefaultJsonOptions() {
    DatagramSocketOptions def = new DatagramSocketOptions();
    DatagramSocketOptions json = new DatagramSocketOptions(new JsonObject());
    assertEquals(def.isBroadcast(), json.isBroadcast());
    assertEquals(def.isLoopbackModeDisabled(), json.isLoopbackModeDisabled());
    assertEquals(def.getMulticastTimeToLive(), json.getMulticastTimeToLive());
    assertEquals(def.getMulticastNetworkInterface(), json.getMulticastNetworkInterface());
    assertEquals(def.isIpV6(), json.isIpV6());
  }

  @Test
  public void testCopyOptionsJson() {
    Random rand = new Random();
    boolean broadcast = rand.nextBoolean();
    boolean loopbackModeDisabled = rand.nextBoolean();
    int multicastTimeToLive = TestUtils.randomPositiveInt();
    String multicastNetworkInterface = TestUtils.randomAlphaString(100);
    boolean reuseAddress = rand.nextBoolean();
    boolean ipV6 = rand.nextBoolean();
    JsonObject json = new JsonObject().put("broadcast", broadcast)
      .put("loopbackModeDisabled", loopbackModeDisabled)
      .put("multicastTimeToLive", multicastTimeToLive)
      .put("multicastNetworkInterface", multicastNetworkInterface)
      .put("reuseAddress", reuseAddress)
      .put("ipV6", ipV6);
    DatagramSocketOptions copy = new DatagramSocketOptions(json);
    assertEquals(broadcast, copy.isBroadcast());
    assertEquals(loopbackModeDisabled, copy.isLoopbackModeDisabled());
    assertEquals(multicastTimeToLive, copy.getMulticastTimeToLive());
    assertEquals(multicastNetworkInterface, copy.getMulticastNetworkInterface());
    assertEquals(reuseAddress, copy.isReuseAddress());
    assertEquals(ipV6, copy.isIpV6());
    testComplete();
  }

  @Test
  public void testOptionsCopied() {
    DatagramSocketOptions options = new DatagramSocketOptions();
    options.setReuseAddress(true);
    peer1 = vertx.createDatagramSocket(options);
    peer2 = vertx.createDatagramSocket(options);
    // Listening on same address:port so will only work if reuseAddress = true
    // Set to false, but because options are copied internally should still work
    options.setReuseAddress(false);
    peer1.listen(1234, "127.0.0.1", ar -> {
      assertTrue(ar.succeeded());
      peer2.listen(1234, "127.0.0.1", ar2 -> {
        assertTrue(ar2.succeeded());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testUseInMultithreadedWorker() throws Exception {
    class MyVerticle extends AbstractVerticle {
      @Override
      public void start() {
        assertIllegalStateException(() -> peer1 = vertx.createDatagramSocket(new DatagramSocketOptions()));
        testComplete();
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true).setMultiThreaded(true));
    await();
  }
}
