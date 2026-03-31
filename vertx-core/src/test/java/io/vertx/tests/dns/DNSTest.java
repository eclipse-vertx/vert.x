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

package io.vertx.tests.dns;

import io.netty.handler.codec.dns.DnsMessage;
import io.netty.resolver.dns.DnsNameResolverTimeoutException;
import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsClientOptions;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.MockDnsServer;
import io.vertx.test.fakedns.MockDnsServer.RecordStore;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.Function;

import io.vertx.test.core.TestUtils;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DNSTest extends VertxTestBase {

  private MockDnsServer mockDnsServer;

  public DNSTest() {
    super(ReportMode.FORBIDDEN);
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();
    mockDnsServer = new MockDnsServer(vertx);
    mockDnsServer.start();
  }

  @Override
  protected void tearDown() throws Exception {
    mockDnsServer.stop();
    super.tearDown();
  }

  @Test
  public void testIllegalArguments() throws Exception {
    mockDnsServer.testResolveAAAA("::1");
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
    //dnsServer.testLookup4(ip);
    mockDnsServer.testLookup4(ip);
    VertxOptions vertxOptions = new VertxOptions();
    InetSocketAddress mockDnsAddress = mockDnsServer.localAddress();
    vertxOptions.getAddressResolverOptions().addServer(mockDnsAddress.getHostString() + ":" + mockDnsAddress.getPort());
    Vertx vertxWithFakeDns = vertx(vertxOptions);
    DnsClient dnsClient = clientProvider.apply(vertxWithFakeDns);

    dnsClient
      .lookup4("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals(ip, result);
        testComplete();
      }));
    await();
    vertxWithFakeDns.close();
  }

  @Test
  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    mockDnsServer.testResolveA(ip);
    DnsClient dns = prepareDns();

    dns
      .resolveA("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(ip, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testUnresolvedDnsServer() throws Exception {
    try {
      DnsClient dns = vertx.createDnsClient(new DnsClientOptions().setHost("iamanunresolvablednsserver.com").setPort(53));
      Assert.fail();
    } catch (Exception e) {
      Assert.assertTrue(e instanceof IllegalArgumentException);
      Assert.assertEquals("Cannot resolve the host to a valid ip address", e.getMessage());
    }
  }

  @Test
  public void testResolveAIpV6() throws Exception {
    final String ip = "10.0.0.1";
    mockDnsServer.testResolveA(ip).ipAddress("::1");
    // force the fake dns server to Ipv6
    DnsClient dns = prepareDns();
    dns
      .resolveA("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(ip, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveAAAA() throws Exception {
    mockDnsServer.testResolveAAAA("::1");
    DnsClient dns = prepareDns();

    dns
      .resolveAAAA("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals("0:0:0:0:0:0:0:1", result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveMX() throws Exception {
    final String mxRecord = "mail.vertx.io";
    final int prio = 10;
    mockDnsServer.testResolveMX(prio, mxRecord);
    DnsClient dns = prepareDns();

    dns
      .resolveMX("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        MxRecord record = result.get(0);
        Assert.assertEquals(100, record.ttl());
        Assert.assertEquals(prio, record.priority());
        Assert.assertEquals(record.name(), mxRecord);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveTXT() throws Exception {
    final String txt = "vertx is awesome";
    mockDnsServer.testResolveTXT(txt);
    DnsClient dns = prepareDns();
    dns
      .resolveTXT("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(txt, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveNS2() throws Exception {
    final String ns = "ns.vertx.io";
    mockDnsServer.testResolveNS(ns);
    DnsClient dns = prepareDns();

    dns
      .resolveNS("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(ns, result.get(0));
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveCNAME() throws Exception {
    final String cname = "cname.vertx.io";
    mockDnsServer.testResolveCNAME(cname);
    DnsClient dns = prepareDns();

    dns
      .resolveCNAME("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        String record = result.get(0);
        Assert.assertFalse(record.isEmpty());
        Assert.assertEquals(cname, record);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolvePTR() throws Exception {
    final String ptr = "ptr.vertx.io";
    mockDnsServer.testResolvePTR(ptr);
    DnsClient dns = prepareDns();

    dns
      .resolvePTR("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals(ptr, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveSRV() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;

    mockDnsServer.testResolveSRV("_svc._tcp.vertx.io", priority, weight, port, "svc.vertx.io");
    DnsClient dns = prepareDns();

    dns
      .resolveSRV("_svc._tcp.vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        SrvRecord record = result.get(0);
        Assert.assertEquals("vertx.io.", record.name());
        Assert.assertEquals("_svc", record.service());
        Assert.assertEquals("_tcp", record.protocol());
        Assert.assertEquals(100, record.ttl());
        Assert.assertEquals(priority, record.priority());
        Assert.assertEquals(weight, record.weight());
        Assert.assertEquals(port, record.port());
        Assert.assertEquals("svc.vertx.io", record.target());
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveSRV2() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;

    mockDnsServer.testResolveSRV2(priority, weight, port, "_svc._tcp.vertx.io");
    DnsClient dns = prepareDns();

    dns
      .resolveSRV("_svc._tcp.vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(2, result.size());
        SortedMap<Integer, SrvRecord> map = new TreeMap<>();
        map.put(result.get(0).port(), result.get(0));
        map.put(result.get(1).port(), result.get(1));
        SrvRecord record = map.get(80);
        Assert.assertEquals("vertx.io.", record.name());
        Assert.assertEquals("_tcp", record.protocol());
        Assert.assertEquals("_svc", record.service());
        Assert.assertEquals(100, record.ttl());
        Assert.assertEquals(priority, record.priority());
        Assert.assertEquals(weight, record.weight());
        Assert.assertEquals(80, record.port());
        Assert.assertEquals("svc0.vertx.io", record.target());
        record = map.get(81);
        Assert.assertEquals("vertx.io.", record.name());
        Assert.assertEquals("_tcp", record.protocol());
        Assert.assertEquals("_svc", record.service());
        Assert.assertEquals(100, record.ttl());
        Assert.assertEquals(priority, record.priority());
        Assert.assertEquals(weight, record.weight());
        Assert.assertEquals(81, record.port());
        Assert.assertEquals("svc1.vertx.io", record.target());
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookup4() throws Exception {
    final String ip = "10.0.0.1";
    mockDnsServer.testLookup4(ip);
    DnsClient dns = prepareDns();
    dns
      .lookup4("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals(ip, result);
        DnsMessage msg = mockDnsServer.pollMessage();
        Assert.assertTrue(msg.isRecursionDesired());
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookup6() throws Exception {
    mockDnsServer.testLookup6("::1");
    DnsClient dns = prepareDns();

    dns
      .lookup6("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals("0:0:0:0:0:0:0:1", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookupWithARecord() throws Exception {
    String ip = "10.0.0.1";
    mockDnsServer.testLookup4(ip);
    DnsClient dns = prepareDns();

    dns
      .lookup("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals(ip, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookupWithAAAARecord() throws Exception {
    mockDnsServer.testLookup6("::1");
    DnsClient dns = prepareDns();

    dns
      .lookup("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals("0:0:0:0:0:0:0:1", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testTimeout() throws Exception {
    DnsClient dns = prepareDns(new DnsClientOptions().setHost("127.0.0.1").setPort(10000).setQueryTimeout(5000));

    dns
      .lookup("vertx.io")
      .onComplete(TestUtils.onFailure(err -> {
        if (err instanceof VertxException) {
          Assert.assertEquals("DNS query timeout for vertx.io.", err.getMessage());
        } else if (err instanceof DnsNameResolverTimeoutException) {
          DnsNameResolverTimeoutException te = (DnsNameResolverTimeoutException) err;
        }
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookupNonExisting() throws Exception {
    mockDnsServer.testLookupNonExisting();
    DnsClient dns = prepareDns();
    dns
      .lookup("gfegjegjf.sg1")
      .onComplete(TestUtils.onFailure(err -> {
        testComplete();
      }));
    await();
  }

  @Test
  public void testReverseLookupIpv4() throws Exception {
    mockDnsServer.testReverseLookup("1.0.0.10.in-addr.arpa");
    DnsClient dns = prepareDns();

    dns
      .reverseLookup("10.0.0.1")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals("vertx.io", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testReverseLookupIpv6() throws Exception {
    String address = "::1";
    mockDnsServer.testReverseLookup("1.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.0.in-addr.arpa");
    DnsClient dns = prepareDns();

    dns
      .reverseLookup(address)
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals("vertx.io", result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testLookup4CNAME() throws Exception {
    final String cname = "cname.vertx.io";
    final String ip = "10.0.0.1";
    mockDnsServer.testLookup4CNAME(cname, ip);
    DnsClient dns = prepareDns();

    dns
      .lookup4("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertEquals(ip, result);
        testComplete();
      }));
    await();
  }

  @Test
  public void testResolveMXWhenDNSRepliesWithDNAMERecord() throws Exception {
    final DnsClient dns = prepareDns();
    mockDnsServer.testResolveDNAME("mail.vertx.io");
    dns.resolveMX("vertx.io")
      .onComplete(TestUtils.onSuccess(lst -> {
        Assert.assertEquals(Collections.emptyList(), lst);
        testComplete();
      }));
    await();
  }

//  @Test
//  public void testLogActivity() throws Exception {
//    TestLoggerFactory factory = testLogging(new DnsClientOptions().setLogActivity(true));
//    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
//  }

  @Test
  public void testRecursionDesired() throws Exception {
    final String ip = "10.0.0.1";

    mockDnsServer.testResolveA(ip);
    DnsClient dns = prepareDns(new DnsClientOptions().setRecursionDesired(true));
    dns
      .resolveA("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(ip, result.get(0));
        DnsMessage msg = mockDnsServer.pollMessage();
        Assert.assertTrue(msg.isRecursionDesired());
        testComplete();
      }));
    await();
  }

  @Test
  public void testRecursionNotDesired() throws Exception {
    final String ip = "10.0.0.1";

    mockDnsServer.testResolveA(ip);
    DnsClient dns = prepareDns(new DnsClientOptions().setRecursionDesired(false));
    dns
      .resolveA("vertx.io")
      .onComplete(TestUtils.onSuccess(result -> {
        Assert.assertFalse(result.isEmpty());
        Assert.assertEquals(1, result.size());
        Assert.assertEquals(ip, result.get(0));
        DnsMessage msg = mockDnsServer.pollMessage();
        Assert.assertFalse(msg.isRecursionDesired());
        testComplete();
      }));
    await();
  }

  @Test
  public void testClose() throws Exception {
    disableThreadChecks();
    waitFor(2);
    String ip = "10.0.0.1";
    RecordStore store = mockDnsServer.testResolveA(ip).store();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    mockDnsServer.store(question -> {
      latch1.countDown();
      try {
        TestUtils.awaitLatch(latch2);
      } catch (Exception e) {
        Assert.fail(e.getMessage());
      }
      return store.getRecords(question);
    });
    DnsClient dns = prepareDns();
    dns
      .resolveA("vertx.io")
      .onComplete(TestUtils.onFailure(timeout -> {
        Assert.assertTrue(timeout.getMessage().contains("closed"));
        complete();
      }));
    TestUtils.awaitLatch(latch1);
    dns.close().onComplete(TestUtils.onSuccess(v -> {
      latch2.countDown();
      complete();
    }));
    await();
  }

  @Test
  public void testIpv6NameServer() {
    // We just want to verify that we can create a client with an IPv6 address as DNS server
    vertx.createDnsClient(new DnsClientOptions().setPort(53).setHost("::1"));
  }

  private DnsClient prepareDns() {
    return prepareDns(new DnsClientOptions().setQueryTimeout(15000));
  }

  private DnsClient prepareDns(DnsClientOptions options) {
    return vertx.createDnsClient(new DnsClientOptions(options).setPort(MockDnsServer.PORT).setHost(MockDnsServer.IP_ADDRESS));
  }
}
