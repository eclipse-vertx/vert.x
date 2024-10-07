/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.datagram;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledHeapByteBuf;
import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.datagram.DatagramSocket;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.internal.buffer.BufferInternal;
import io.vertx.core.impl.Utils;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetworkOptions;
import io.vertx.core.streams.WriteStream;
import io.vertx.core.transport.Transport;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.netty.TestLoggerFactory;
import org.junit.Assume;
import org.junit.Test;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

import static io.vertx.test.core.TestUtils.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DatagramTest extends VertxTestBase {

  private volatile DatagramSocket peer1;
  private volatile DatagramSocket peer2;

  protected void tearDown() throws Exception {
    CountDownLatch latch = new CountDownLatch(2);
    if (peer1 != null) {
      peer1.close().onComplete(onSuccess(v -> latch.countDown()));
    } else {
      latch.countDown();
    }
    if (peer2 != null) {
      peer2.close().onComplete(onSuccess(v -> latch.countDown()));
    } else {
      latch.countDown();
    }
    awaitLatch(latch);
    super.tearDown();
  }

  @Test
  public void testDatagramSocket() throws Exception {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());

    assertNullPointerException(() -> peer1.send((Buffer) null, 1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.send(Buffer.buffer(), -1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.send(Buffer.buffer(), 65536, "127.0.0.1"));

    assertNullPointerException(() -> peer1.send((String) null, 1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.send("", -1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.send("", 65536, "127.0.0.1"));

    assertNullPointerException(() -> peer1.send((String) null, "UTF-8", 1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.send("", "UTF-8", -1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.send("", "UTF-8", 65536, "127.0.0.1"));
    assertNullPointerException(() -> peer1.send("", null, 1, "127.0.0.1"));

    assertIllegalArgumentException(() -> peer1.sender(-1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.sender(65536, "127.0.0.1"));
    assertNullPointerException(() -> peer1.sender(1, null));

    assertIllegalArgumentException(() -> peer1.listen(-1, "127.0.0.1"));
    assertIllegalArgumentException(() -> peer1.listen(65536, "127.0.0.1"));
    assertNullPointerException(() -> peer1.listen(1, null));
  }

  @Test
  public void testSendReceive() throws Exception {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(128);
    CountDownLatch latch = new CountDownLatch(1);
    Context serverContext = vertx.getOrCreateContext();
    serverContext.runOnContext(v -> {
      peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
      peer2.exceptionHandler(t -> fail(t.getMessage()));
      peer2.handler(packet -> {
        assertSame(serverContext, Vertx.currentContext());
        assertFalse(Thread.holdsLock(peer2));
        Buffer data = packet.data();
        ByteBuf buff = ((BufferInternal)data).getByteBuf();
        while (buff != buff.unwrap() && buff.unwrap() != null) {
          buff = buff.unwrap();
        }

        assertTrue("Was expecting an unpooled buffer instead of " + buff.getClass().getSimpleName(), buff instanceof UnpooledHeapByteBuf);
        assertEquals(expected, data);
        complete();
      });
      peer2
        .listen(1234, "127.0.0.1")
        .onComplete(onSuccess(so -> latch.countDown()));
    });
    awaitLatch(latch);
    Context clientContext = vertx.getOrCreateContext();
    clientContext.runOnContext(v -> {
      peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
      peer1.send(expected, 1234, "127.0.0.1")
        .onComplete(onSuccess(s -> {
        assertSame(clientContext, Vertx.currentContext());
        assertFalse(Thread.holdsLock(peer1));
        complete();
      }));
    });
    await();
  }

  @Test
  public void testSendReceiveLargePacket() {
    int packetSize = 10000;
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions().setSendBufferSize(packetSize));
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions().setReceiveBufferSize(packetSize + 16)); // OSX needs 16 more
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1").onComplete(onSuccess(v -> {
      Buffer buffer = TestUtils.randomBuffer(packetSize);
      peer2.handler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, 1234, "127.0.0.1").onComplete(onSuccess(ar2 -> {}));
    }));
    await();
  }



  @Test
  public void testSender() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2
      .listen(1234, "127.0.0.1")
      .onComplete(onSuccess(ar -> {
        Buffer buffer = TestUtils.randomBuffer(128);
        peer2.handler(packet -> {
          assertEquals(buffer, packet.data());
          testComplete();
        });

        WriteStream<Buffer> sender1 = peer1.sender(1234, "127.0.0.1");
        sender1.write(buffer);
      }));
    await();
  }

  @Test
  public void testListenHostPort() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2
      .listen(1234, "127.0.0.1")
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testListenPort() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2
      .listen(1234, "localhost")
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testListenInetSocketAddress() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2
      .listen(1234, "127.0.0.1")
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testListenSamePortMultipleTimes() {
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2.listen(1234, "127.0.0.1")
      .onComplete(onSuccess(v -> peer1
        .listen(1234, "127.0.0.1")
        .onComplete(onFailure(err -> testComplete()))));
    await();
  }

  @Test
  public void testEcho() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.exceptionHandler(t -> fail(t.getMessage()));
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "127.0.0.1").onComplete(onSuccess(v -> {
      Buffer buffer = TestUtils.randomBuffer(128);
      peer2.handler(packet -> {
        assertEquals("127.0.0.1", packet.sender().host());
        assertEquals(1235, packet.sender().port());
        assertEquals(buffer, packet.data());
        peer2.send(packet.data(), 1235, "127.0.0.1").onComplete(onSuccess(v2 -> {}));
      });
      peer1.listen(1235, "127.0.0.1").onComplete(onSuccess(v2 -> {
        peer1.handler(packet -> {
          assertEquals(buffer, packet.data());
          assertEquals("127.0.0.1", packet.sender().host());
          assertEquals(1234, packet.sender().port());
          testComplete();
        });
        peer1.send(buffer, 1234, "127.0.0.1").onComplete(onSuccess(v3 -> {}));
      }));
    }));
    await();
  }

  @Test
  public void testSendAfterCloseFails() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.close().onComplete(onSuccess(v1 -> {
      peer1.send("Test", 1234, "127.0.0.1").onComplete(onFailure(err1 -> {
        peer1 = null;
        peer2.close().onComplete(onSuccess(v2 -> {
          peer2.send("Test", 1234, "127.0.0.1").onComplete(onFailure(err2 -> {
            peer2 = null;
            testComplete();
          }));
        }));
      }));
    }));
    await();
  }

  @Test
  public void testBroadcast() {
    if (TRANSPORT != Transport.NIO) {
      return;
    }
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions().setBroadcast(true));
    peer2 = vertx.createDatagramSocket(new DatagramSocketOptions().setBroadcast(true));
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen(1234, "0.0.0.0").onComplete(onSuccess(v -> {
      Buffer buffer = TestUtils.randomBuffer(128);
      peer2.handler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, 1234, "255.255.255.255").onComplete(onSuccess(v2 -> {}));
    }));
    await();
  }

  @Test
  public void testBroadcastFailsIfNotConfigured() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.send("test", 1234, "255.255.255.255").onComplete(onFailure(err -> testComplete()));
    await();
  }

  @Test
  public void testPause() {

  }

  @Test
  public void testMulticastJoinLeave() throws Exception {
    String iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1")).getName();
    testMulticastJoinLeave("0.0.0.0", new DatagramSocketOptions(), new DatagramSocketOptions().setMulticastNetworkInterface(iface), (groupAddress, handler) -> {
      peer1.listenMulticastGroup(groupAddress, iface, null).onComplete(handler);
    }, (groupAddress, handler) -> {
      peer1.unlistenMulticastGroup(groupAddress, iface, null).onComplete(handler);
    });
  }

  @Test
  public void testMulticastJoinLeaveReuseMulticastNetworkInterface() throws Exception {
    String iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1")).getName();
    DatagramSocketOptions options = new DatagramSocketOptions().setMulticastNetworkInterface(iface);
    testMulticastJoinLeave("0.0.0.0", options, options, (groupAddress, handler) -> {
      peer1.listenMulticastGroup(groupAddress).onComplete(handler);
    }, (groupAddress, handler) -> {
      peer1.unlistenMulticastGroup(groupAddress).onComplete(handler);
    });
  }

  @Test
  public void testMulticastJoinLeaveBindOnMulticastGroup() throws Exception {
    Assume.assumeFalse(Utils.isWindows());
    String iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1")).getName();
    DatagramSocketOptions options = new DatagramSocketOptions().setMulticastNetworkInterface(iface);
    testMulticastJoinLeave("230.0.0.1", options, options, (groupAddress, handler) -> {
      peer1.listenMulticastGroup(groupAddress).onComplete(handler);
    }, (groupAddress, handler) -> {
      peer1.unlistenMulticastGroup(groupAddress).onComplete(handler);
    });
  }

  private void testMulticastJoinLeave(String bindAddress,
                                      DatagramSocketOptions options1,
                                      DatagramSocketOptions options2,
                                      BiConsumer<String, Handler<AsyncResult<Void>>> join,
                                      BiConsumer<String, Handler<AsyncResult<Void>>> leave) {
    if (TRANSPORT != Transport.NIO) {
      return;
    }
    Buffer buffer = Buffer.buffer("HELLO");
    String groupAddress = "230.0.0.1";
    AtomicBoolean received = new AtomicBoolean();
    peer1 = vertx.createDatagramSocket(options1);
    peer2 = vertx.createDatagramSocket(options2);

    peer1.handler(packet -> {
      assertEquals(buffer, packet.data());
      received.set(true);
    });

    peer1.listen(1234, bindAddress).onComplete(onSuccess(v1 -> {
      join.accept(groupAddress, onSuccess(v2 -> {
        peer2.send(buffer, 1234, groupAddress).onComplete(onSuccess(ar3 -> {
          // leave group in 1 second so give it enough time to really receive the packet first
          vertx.setTimer(1000, id -> {
            leave.accept(groupAddress, onSuccess(ar4 -> {
              AtomicBoolean receivedAfter = new AtomicBoolean();
              peer1.handler(packet -> {
                // Should not receive any more event as it left the group
                receivedAfter.set(true);
              });
              peer2.send(buffer, 1234, groupAddress).onComplete(onSuccess(v5 -> {
                // schedule a timer which will check in 1 second if we received a message after the group
                // was left before
                vertx.setTimer(1000, id2 -> {
                  assertFalse(receivedAfter.get());
                  assertTrue(received.get());
                  testComplete();
                });
              }));
            }));
          });
        }));
      }));
    }));
    await();
  }

  @Test
  public void testMulticastJoinWithoutNetworkInterface() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.listenMulticastGroup("230.0.0.1").onComplete(onFailure(err -> testComplete()));
    await();
  }

  @Test
  public void testMulticastLeaveWithoutNetworkInterface() {
    peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
    peer1.unlistenMulticastGroup("230.0.0.1").onComplete(onFailure(err -> testComplete()));
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
    assertIllegalArgumentException(() -> options.setTrafficClass(-2));
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

  // Does not pass with native on OSX - it requires SO_REUSEPORT
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
    options.setReusePort(true); // Seems needed only for native on OSX
    peer1 = vertx.createDatagramSocket(options);
    peer2 = vertx.createDatagramSocket(options);
    // Listening on same address:port so will only work if reuseAddress = true
    // Set to false, but because options are copied internally should still work
    options.setReuseAddress(false);
    peer1.listen(1234, "127.0.0.1")
      .compose(v -> peer2
        .listen(1234, "127.0.0.1"))
      .onComplete(onSuccess(v -> testComplete()));
    await();
  }

  @Test
  public void testNoLogging() throws Exception {
    TestLoggerFactory factory = testLogging(new DatagramSocketOptions(), new DatagramSocketOptions());
    assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testSendLogging() throws Exception {
    TestLoggerFactory factory = testLogging(new DatagramSocketOptions().setLogActivity(true), new DatagramSocketOptions());
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testListenLogging() throws Exception {
    TestLoggerFactory factory = testLogging(new DatagramSocketOptions(), new DatagramSocketOptions().setLogActivity(true));
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  private TestLoggerFactory testLogging(DatagramSocketOptions sendOptions, DatagramSocketOptions listenOptions) throws Exception {
    return TestUtils.testLogging(() -> {
      peer1 = vertx.createDatagramSocket(sendOptions);
      peer2 = vertx.createDatagramSocket(listenOptions);
      peer2.exceptionHandler(t -> fail(t.getMessage()));
      peer2.listen(1234, "127.0.0.1").onComplete(onSuccess(ar -> {
        Buffer buffer = TestUtils.randomBuffer(128);
        peer2.handler(packet -> {
          assertEquals(buffer, packet.data());
          testComplete();
        });
        peer1.send(buffer, 1234, "127.0.0.1").onComplete(onSuccess(v -> {}));
      }));
      await();
    });
  }

  @Test
  public void testWorker() {
    waitFor(2);
    Buffer expected = TestUtils.randomBuffer(128);
    vertx.deployVerticle(new AbstractVerticle() {
        @Override
        public void start(Promise<Void> startPromise) {
          peer2 = vertx.createDatagramSocket(new DatagramSocketOptions());
          peer2.exceptionHandler(t -> fail(t.getMessage()));
          peer2.handler(packet -> {
            assertTrue(Context.isOnWorkerThread());
            assertSame(context, Vertx.currentContext());
            complete();
          });
          peer2.listen(1234, "127.0.0.1")
            .<Void>mapEmpty()
            .onComplete(startPromise);
        }
      }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER))
      .onComplete(onSuccess(id -> {
        peer1 = vertx.createDatagramSocket(new DatagramSocketOptions());
        peer1
          .send(expected, 1234, "127.0.0.1")
          .onComplete(onSuccess(s -> complete()));
      }));
    await();
  }
}
