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

package io.vertx.core.dns;

import static io.vertx.test.core.TestUtils.assertNullPointerException;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.Ignore;
import org.junit.Test;

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.test.fakedns.FakeDNSServer;
import io.vertx.test.netty.TestLoggerFactory;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DNSTest extends VertxTestBase {

  private FakeDNSServer dnsServer;

  @Override
  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer();
    dnsServer.start();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }

  @Test
  public void testIllegalArguments() throws Exception {
    dnsServer.testResolveAAAA("::1");
    DnsClient dns = prepareDns();

    assertNullPointerException(() -> dns.lookup(null));
    assertNullPointerException(() -> dns.lookup4(null));
    assertNullPointerException(() -> dns.lookup6(null));
    assertNullPointerException(() -> dns.resolveA(null));
    assertNullPointerException(() -> dns.resolveAAAA(null));
    assertNullPointerException(() -> dns.resolveCNAME(null));
    assertNullPointerException(() -> dns.resolveMX(null));
    assertNullPointerException(() -> dns.resolveTXT(null));
    assertNullPointerException(() -> dns.resolvePTR(null));
    assertNullPointerException(() -> dns.resolveNS(null));
    assertNullPointerException(() -> dns.resolveSRV(null));
  }

  @Test
  public void testDefaultDnsClient() throws Exception {
    testDefaultDnsClient(vertx -> vertx.createDnsClient());
  }

  @Test
  public void testDefaultDnsClientWithOptions() throws Exception {
    testDefaultDnsClient(vertx -> vertx.createDnsClient(new DnsClientOptions()));
  }

  private void testDefaultDnsClient(Function<Vertx, DnsClient> clientProvider) throws Exception {
    final String ip = "10.0.0.1";
    dnsServer.testLookup4(ip);
    VertxOptions vertxOptions = new VertxOptions();
    InetSocketAddress fakeServerAddress = dnsServer.localAddress();
    vertxOptions.getAddressResolverOptions().addServer(fakeServerAddress.getHostString() + ":" + fakeServerAddress.getPort());
    Vertx vertxWithFakeDns = Vertx.vertx(vertxOptions);
    DnsClient dnsClient = clientProvider.apply(vertxWithFakeDns);

    dnsClient
      .lookup4("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals(ip, result);
        testComplete();
      }));
    await();
    vertxWithFakeDns.close();
  }

  @Test
  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    dnsServer.testResolveA(ip);
    DnsClient dns = prepareDns();

    dns
      .resolveA("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(ip, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testUnresolvedDnsServer() throws Exception {
    try {
      DnsClient dns = vertx.createDnsClient(new DnsClientOptions().setHost("iamanunresolvablednsserver.com").setPort(53));
      fail();
    } catch (Exception e) {
      assertTrue(e instanceof IllegalArgumentException);
      assertEquals("Cannot resolve the host to a valid ip address", e.getMessage());
    }
  }

  @Test
  public void testResolveAIpV6() throws Exception {
    final String ip = "10.0.0.1";
    dnsServer.testResolveA(ip).ipAddress("::1");
    // force the fake dns server to Ipv6
    DnsClient dns = prepareDns();
    dns
      .resolveA("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(ip, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveAAAA() throws Exception {
    dnsServer.testResolveAAAA("::1");
    DnsClient dns = prepareDns();

    dns
      .resolveAAAA("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals("0:0:0:0:0:0:0:1", result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveMX() throws Exception {
    final String mxRecord = "mail.vertx.io";
    final int prio = 10;
    dnsServer.testResolveMX(prio, mxRecord);
    DnsClient dns = prepareDns();

    dns
      .resolveMX("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        MxRecord record = result.get(0);
        assertEquals(100, record.ttl());
        assertEquals(prio, record.priority());
        assertEquals(record.name(), mxRecord);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveTXT() throws Exception {
    final String txt = "vertx is awesome";
    dnsServer.testResolveTXT(txt);
    DnsClient dns = prepareDns();
    dns
      .resolveTXT("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(txt, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveNS2() throws Exception {
    final String ns = "ns.vertx.io";
    dnsServer.testResolveNS(ns);
    DnsClient dns = prepareDns();

    dns
      .resolveNS("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(ns, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveCNAME() throws Exception {
    final String cname = "cname.vertx.io";
    dnsServer.testResolveCNAME(cname);
    DnsClient dns = prepareDns();

    dns
      .resolveCNAME("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        String record = result.get(0);
        assertFalse(record.isEmpty());
        assertEquals(cname, record);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolvePTR() throws Exception {
    final String ptr = "ptr.vertx.io";
    dnsServer.testResolvePTR(ptr);
    DnsClient dns = prepareDns();

    dns
      .resolvePTR("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals(ptr, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveSRV() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;

    dnsServer.testResolveSRV("_svc._tcp.vertx.io", priority, weight, port, "svc.vertx.io");
    DnsClient dns = prepareDns();

    dns
      .resolveSRV("_svc._tcp.vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        SrvRecord record = result.get(0);
        assertEquals("vertx.io.", record.name());
        assertEquals("_svc", record.service());
        assertEquals("_tcp", record.protocol());
        assertEquals(100, record.ttl());
        assertEquals(priority, record.priority());
        assertEquals(weight, record.weight());
        assertEquals(port, record.port());
        assertEquals("svc.vertx.io", record.target());
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveSRV2() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;

    dnsServer.testResolveSRV2(priority, weight, port, "_svc._tcp.vertx.io");
    DnsClient dns = prepareDns();

    dns
      .resolveSRV("_svc._tcp.vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(2, result.size());
        SortedMap<Integer, SrvRecord> map = new TreeMap<>();
        map.put(result.get(0).port(), result.get(0));
        map.put(result.get(1).port(), result.get(1));
        SrvRecord record = map.get(80);
        assertEquals("vertx.io.", record.name());
        assertEquals("_tcp", record.protocol());
        assertEquals("_svc", record.service());
        assertEquals(100, record.ttl());
        assertEquals(priority, record.priority());
        assertEquals(weight, record.weight());
        assertEquals(80, record.port());
        assertEquals("svc0.vertx.io", record.target());
        record = map.get(81);
        assertEquals("vertx.io.", record.name());
        assertEquals("_tcp", record.protocol());
        assertEquals("_svc", record.service());
        assertEquals(100, record.ttl());
        assertEquals(priority, record.priority());
        assertEquals(weight, record.weight());
        assertEquals(81, record.port());
        assertEquals("svc1.vertx.io", record.target());
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookup4() throws Exception {
    final String ip = "10.0.0.1";
    dnsServer.testLookup4(ip);
    DnsClient dns = prepareDns();
    dns
      .lookup4("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals(ip, result);
        DnsMessage msg = dnsServer.pollMessage();
        assertTrue(msg.isRecursionDesired());
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookup6() throws Exception {
    dnsServer.testLookup6("::1");
    DnsClient dns = prepareDns();

    dns
      .lookup6("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals("0:0:0:0:0:0:0:1", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookupWithARecord() throws Exception {
    String ip = "10.0.0.1";
    dnsServer.testLookup4(ip);
    DnsClient dns = prepareDns();

    dns
      .lookup("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals(ip, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookupWithAAAARecord() throws Exception {
    dnsServer.testLookup6("::1");
    DnsClient dns = prepareDns();

    dns
      .lookup("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals("0:0:0:0:0:0:0:1", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testTimeout() throws Exception {
    DnsClient dns = prepareDns(new DnsClientOptions().setHost("127.0.0.1").setPort(10000).setQueryTimeout(5000));

    dns
      .lookup("vertx.io")
      .onComplete(onFailure(err -> {
        if (err instanceof VertxException) {
          assertEquals("DNS query timeout for vertx.io.", err.getMessage());
        } else if (err instanceof DnsNameResolverTimeoutException) {
          DnsNameResolverTimeoutException te = (DnsNameResolverTimeoutException) err;
        }
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookupNonExisting() throws Exception {
    dnsServer.testLookupNonExisting();
    DnsClient dns = prepareDns();
    dns
      .lookup("gfegjegjf.sg1")
      .onComplete(onFailure(err -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testReverseLookupIpv4() throws Exception {
    dnsServer.testReverseLookup("1.0.0.10.in-addr.arpa");
    DnsClient dns = prepareDns();

    dns
      .reverseLookup("10.0.0.1")
      .onComplete(onSuccess(result -> {
        assertEquals("vertx.io", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testReverseLookupIpv6() throws Exception {
    String address = "::1";
    dnsServer.testReverseLookup("1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.in-addr.arpa");
    DnsClient dns = prepareDns();

    dns
      .reverseLookup(address)
      .onComplete(onSuccess(result -> {
        assertEquals("vertx.io", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookup4CNAME() throws Exception {
    final String cname = "cname.vertx.io";
    final String ip = "10.0.0.1";
    dnsServer.testLookup4CNAME(cname, ip);
    DnsClient dns = prepareDns();

    dns
      .lookup4("vertx.io")
      .onComplete(onSuccess(result -> {
        assertEquals(ip, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveMXWhenDNSRepliesWithDNAMERecord() throws Exception {
    final DnsClient dns = prepareDns();
    dnsServer.testResolveDNAME("mail.vertx.io");
    dns.resolveMX("vertx.io")
      .onComplete(onSuccess(lst -> {
        assertEquals(Collections.emptyList(), lst);
        testComplete();
      }));
    await();
  }

  private TestLoggerFactory testLogging(DnsClientOptions options) {
    final String ip = "10.0.0.1";
    dnsServer.testResolveA(ip);
    return TestUtils.testLogging(() -> {
      try {
        prepareDns(options)
          .resolveA(ip)
          .onComplete(fut -> testComplete());
        await();
      } catch (Exception e) {
        fail(e);
      }
    });
  }

//  @Test
//  public void testLogActivity() throws Exception {
//    TestLoggerFactory factory = testLogging(new DnsClientOptions().setLogActivity(true));
//    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
//  }

  @Test
  public void testDoNotLogActivity() throws Exception {
    TestLoggerFactory factory = testLogging(new DnsClientOptions().setLogActivity(false));
    assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testRecursionDesired() throws Exception {
    final String ip = "10.0.0.1";

    dnsServer.testResolveA(ip);
    DnsClient dns = prepareDns(new DnsClientOptions().setRecursionDesired(true));
    dns
      .resolveA("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(ip, result.get(0));
        DnsMessage msg = dnsServer.pollMessage();
        assertTrue(msg.isRecursionDesired());
        testComplete();
      }));
    await();
  }

  @Test
  public void testRecursionNotDesired() throws Exception {
    final String ip = "10.0.0.1";

    dnsServer.testResolveA(ip);
    DnsClient dns = prepareDns(new DnsClientOptions().setRecursionDesired(false));
    dns
      .resolveA("vertx.io")
      .onComplete(onSuccess(result -> {
        assertFalse(result.isEmpty());
        assertEquals(1, result.size());
        assertEquals(ip, result.get(0));
        DnsMessage msg = dnsServer.pollMessage();
        assertFalse(msg.isRecursionDesired());
        testComplete();
      }));
    await();
  }

  @Test
  public void testClose() throws Exception {
    disableThreadChecks();
    waitFor(2);
    String ip = "10.0.0.1";
    RecordStore store = dnsServer.testResolveA(ip).store();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    dnsServer.store(question -> {
      latch1.countDown();
      try {
        awaitLatch(latch2);
      } catch (Exception e) {
        fail(e);
      }
      return store.getRecords(question);
    });
    DnsClient dns = prepareDns();
    dns
      .resolveA("vertx.io")
      .onComplete(onFailure(timeout -> {
        assertTrue(timeout.getMessage().contains("closed"));
        complete();
      }));
    awaitLatch(latch1);
    dns.close().onComplete(onSuccess(v -> {
      latch2.countDown();
      complete();
    }));
    await();
  }

  private DnsClient prepareDns() throws Exception {
    return prepareDns(new DnsClientOptions().setQueryTimeout(15000));
  }

  private DnsClient prepareDns(DnsClientOptions options) throws Exception {
    InetSocketAddress addr = dnsServer.localAddress();
    return vertx.createDnsClient(new DnsClientOptions(options).setPort(addr.getPort()).setHost(addr.getAddress().getHostAddress()));
  }
}
