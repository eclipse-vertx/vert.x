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

package org.vertx.java.tests.newtests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.datagram.DatagramSocket;
import org.vertx.java.core.datagram.InternetProtocolFamily;

import java.net.*;
import java.util.Enumeration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.vertx.java.tests.newtests.TestUtils.*;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DatagramTest extends VertxTestBase {

  private volatile DatagramSocket peer1;
  private volatile DatagramSocket peer2;

  @After
  public void after() throws Exception {
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
  }

  @Test
  public void testSendReceive() {
    peer1 = vertx.createDatagramSocket(null);
    peer2 = vertx.createDatagramSocket(null);
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen("127.0.0.1", 1234, ar -> {
      assertTrue(ar.succeeded());
      Buffer buffer = randomBuffer(128);
      peer2.dataHandler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, "127.0.0.1", 1234, ar2 -> assertTrue(ar2.succeeded()));
    });
    await();
  }

  @Test
  public void testListenHostPort() {
    peer2 = vertx.createDatagramSocket(null);
    peer2.listen("127.0.0.1", 1234, ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenPort() {
    peer2 = vertx.createDatagramSocket(null);
    peer2.listen(1234, ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenInetSocketAddress() {
    peer2 = vertx.createDatagramSocket(null);
    peer2.listen(new InetSocketAddress("127.0.0.1", 1234), ar -> {
      assertTrue(ar.succeeded());
      testComplete();
    });
    await();
  }

  @Test
  public void testListenSamePortMultipleTimes() {
    peer2 = vertx.createDatagramSocket(null);
    peer1 = vertx.createDatagramSocket(null);
    peer2.listen(1234, ar1 -> {
      assertTrue(ar1.succeeded());
      peer1.listen(1234, ar2 -> {
        assertTrue(ar2.failed());
        testComplete();
      });
    });
    await();
  }

  @Test
  public void testEcho() {
    peer1 = vertx.createDatagramSocket(null);
    peer2 = vertx.createDatagramSocket(null);
    peer1.exceptionHandler(t -> fail(t.getMessage()));
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.listen("127.0.0.1", 1234, ar -> {
      assertTrue(ar.succeeded());
      Buffer buffer = randomBuffer(128);
      peer2.dataHandler(packet -> {
        assertEquals(new InetSocketAddress("127.0.0.1", 1235), packet.sender());
        assertEquals(buffer, packet.data());
        peer2.send(packet.data(), "127.0.0.1", 1235, ar2 -> assertTrue(ar2.succeeded()));
      });
      peer1.listen("127.0.0.1", 1235, ar2 -> {
        peer1.dataHandler(packet -> {
          assertEquals(buffer, packet.data());
          assertEquals(new InetSocketAddress("127.0.0.1", 1234), packet.sender());
          testComplete();
        });
        peer1.send(buffer, "127.0.0.1", 1234, ar3 -> assertTrue(ar3.succeeded()));
      });
    });
    await();
  }

  @Test
  public void testSendAfterCloseFails() {
    peer1 = vertx.createDatagramSocket(null);
    peer2 = vertx.createDatagramSocket(null);
    peer1.close(ar -> {
      assertTrue(ar.succeeded());
      peer1.send("Test", "127.0.0.1", 1234, ar2 -> {
        assertTrue(ar2.failed());
        peer1 = null;
        peer2.close(ar3 -> {
          assertTrue(ar3.succeeded());
            peer2.send("Test", "127.0.0.1", 1234, ar4 -> {
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
    peer1 = vertx.createDatagramSocket(null);
    peer2 = vertx.createDatagramSocket(null);
    peer2.exceptionHandler(t -> fail(t.getMessage()));
    peer2.setBroadcast(true);
    peer1.setBroadcast(true);
    peer2.listen(new InetSocketAddress(1234), ar1 -> {
      assertTrue(ar1.succeeded());
      Buffer buffer = randomBuffer(128);
      peer2.dataHandler(packet -> {
        assertEquals(buffer, packet.data());
        testComplete();
      });
      peer1.send(buffer, "255.255.255.255", 1234, ar2 -> {
        assertTrue(ar2.succeeded());
      });
    });
    await();
  }

  @Test
  public void testBroadcastFailsIfNotConfigured() {
    peer1 = vertx.createDatagramSocket(null);
    peer1.send("test", "255.255.255.255", 1234, ar -> {
      assertTrue(ar.failed());
      testComplete();
    });
    await();
  }

  @Test
  public void testConfigureAfterSendString() {
    peer1 = vertx.createDatagramSocket(null);
    peer1.send("test", "127.0.0.1", 1234, null);
    checkConfigure(peer1);
  }

  @Test
  public void testConfigureAfterSendStringWithEnc() {
    peer1 = vertx.createDatagramSocket(null);
    peer1.send("test", "UTF-8", "127.0.0.1", 1234, null);
    checkConfigure(peer1);
  }

  @Test
  public void testConfigureAfterSendBuffer() {
    peer1 = vertx.createDatagramSocket(null);
    peer1.send(randomBuffer(64), "127.0.0.1", 1234, null);
    checkConfigure(peer1);
  }

  @Test
  public void testConfigureAfterListen() {
    peer1 = vertx.createDatagramSocket(null);
    peer1.listen("127.0.0.1", 1234, null);
    checkConfigure(peer1);
  }

  @Test
  public void testConfigureAfterListenWithInetSocketAddress() {
    peer1 = vertx.createDatagramSocket(null);
    peer1.listen(new InetSocketAddress("127.0.0.1", 1234), null);
    checkConfigure(peer1);
  }

  @Test
  public void testConfigure() throws Exception {
    peer1 = vertx.createDatagramSocket(InternetProtocolFamily.IPv4);

    assertFalse(peer1.isBroadcast());
    peer1.setBroadcast(true);
    assertTrue(peer1.isBroadcast());

    assertTrue(peer1.isMulticastLoopbackMode());
    peer1.setMulticastLoopbackMode(false);
    assertFalse(peer1.isMulticastLoopbackMode());

    NetworkInterface iface = null;
    Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
    while (ifaces.hasMoreElements()) {
      NetworkInterface networkInterface = ifaces.nextElement();
      try {
        if (networkInterface.supportsMulticast()) {
          Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
          while (addresses.hasMoreElements()) {
            if (addresses.nextElement() instanceof Inet4Address) {
              iface = networkInterface;
              break;
            }
          }
        }
      } catch (SocketException e) {
        // ignore
      }
    }

    if (iface != null) {
      assertNull(peer1.getMulticastNetworkInterface());
      peer1.setMulticastNetworkInterface(iface.getName());
      assertEquals(iface.getName(), peer1.getMulticastNetworkInterface());
    }

    peer1.setReceiveBufferSize(1024);
    peer1.setSendBufferSize(1024);

    assertFalse(peer1.isReuseAddress());
    peer1.setReuseAddress(true);
    assertTrue(peer1.isReuseAddress());

    assertNotSame(2, peer1.getMulticastTimeToLive());
    peer1.setMulticastTimeToLive(2);
    assertEquals(2, peer1.getMulticastTimeToLive());

    testComplete();
  }

  private void checkConfigure(DatagramSocket endpoint)  {
    try {
      endpoint.setBroadcast(true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setMulticastLoopbackMode(true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setMulticastNetworkInterface(NetworkInterface.getNetworkInterfaces().nextElement().getName());
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    } catch (SocketException e) {
      // ignore
    }

    try {
      endpoint.setReceiveBufferSize(1024);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setReuseAddress(true);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setSendBufferSize(1024);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setMulticastTimeToLive(2);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }

    try {
      endpoint.setTrafficClass(1);
      fail("Should throw exception");
    } catch (IllegalStateException e) {
      // expected
    }
    testComplete();
  }

  @Test
  public void testMulticastJoinLeave() throws Exception {
    final Buffer buffer = randomBuffer(128);
    final String groupAddress = "230.0.0.1";
    final String iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1")).getName();
    final AtomicBoolean received = new AtomicBoolean();
    peer1 = vertx.createDatagramSocket(InternetProtocolFamily.IPv4);
    peer2 = vertx.createDatagramSocket(InternetProtocolFamily.IPv4);

    peer1.setMulticastNetworkInterface(iface);
    peer2.setMulticastNetworkInterface(iface);

    peer1.dataHandler(packet -> {
      assertEquals(buffer, packet.data());
      received.set(true);
    });

    peer1.listen(1234, ar1 -> {
      assertTrue(ar1.succeeded());
      peer1.listenMulticastGroup(groupAddress, iface, null, ar2 -> {
        assertTrue(ar2.succeeded());
        peer2.send(buffer, groupAddress, 1234, ar3 -> {
          assertTrue(ar3.succeeded());
          // leave group in 1 second so give it enough time to really receive the packet first
          vertx.setTimer(1000, id -> {
            peer1.unlistenMulticastGroup(groupAddress, iface, null, ar4 -> {
              assertTrue(ar4.succeeded());
              AtomicBoolean receivedAfter = new AtomicBoolean();
              peer1.dataHandler(packet -> {
                // Should not receive any more event as it left the group
                receivedAfter.set(true);
              });
              peer2.send(buffer, groupAddress, 1234, ar5 -> {
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
}
