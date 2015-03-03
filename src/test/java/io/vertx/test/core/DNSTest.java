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
import io.vertx.core.dns.DnsClient;
import io.vertx.core.dns.DnsException;
import io.vertx.core.dns.DnsResponseCode;
import io.vertx.core.dns.MxRecord;
import io.vertx.core.dns.SrvRecord;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.List;

import static io.vertx.test.core.TestUtils.assertIllegalStateException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DNSTest extends VertxTestBase {

  private FakeDNSServer dnsServer;

  @Test
  public void testIllegalArguments() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testResolveAAAA("::1"));

    assertNullPointerException(() -> dns.lookup(null, ar -> {}));
    assertNullPointerException(() -> dns.lookup4(null, ar -> {}));
    assertNullPointerException(() -> dns.lookup6(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveA(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveAAAA(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveCNAME(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveMX(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveTXT(null, ar -> {}));
    assertNullPointerException(() -> dns.resolvePTR(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveNS(null, ar -> {}));
    assertNullPointerException(() -> dns.resolveSRV(null, ar -> {}));

    dnsServer.stop();
  }

  @Test
  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveA(ip));

    dns.resolveA("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ip, result.get(0));
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveAAAA() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testResolveAAAA("::1"));

    dns.resolveAAAA("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals("0:0:0:0:0:0:0:1", result.get(0));
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveMX() throws Exception {
    final String mxRecord = "mail.vertx.io";
    final int prio = 10;
    DnsClient dns = prepareDns(FakeDNSServer.testResolveMX(prio, mxRecord));

    dns.resolveMX("vertx.io", ar -> {
      List<MxRecord> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      MxRecord record = result.get(0);
      assertEquals(prio, record.priority());
      assertEquals(record.name(), mxRecord);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveTXT() throws Exception {
    final String txt = "vertx is awesome";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveTXT(txt));

    dns.resolveTXT("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(txt, result.get(0));
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveNS() throws Exception {
    final String ns = "ns.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveNS(ns));

    dns.resolveNS("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ns, result.get(0));
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveCNAME() throws Exception {
    final String cname = "cname.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testResolveCNAME(cname));

    dns.resolveCNAME("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      String record = result.get(0);
      assertFalse(record.isEmpty());
      assertEquals(cname, record);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolvePTR() throws Exception {
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testResolvePTR(ptr));

    dns.resolvePTR("10.0.0.1.in-addr.arpa", ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals(ptr, result);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testResolveSRV() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;
    final String target = "vertx.io";

    DnsClient dns = prepareDns(FakeDNSServer.testResolveSRV(priority, weight, port, target));

    dns.resolveSRV("vertx.io", ar -> {
      List<SrvRecord> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());

      SrvRecord record = result.get(0);

      assertEquals(priority, record.priority());
      assertEquals(weight, record.weight());
      assertEquals(port, record.port());
      assertEquals(target, record.target());

      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookup4() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(FakeDNSServer.testLookup4(ip));

    dns.lookup4("vertx.io", ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals(ip, result);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookup6() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testLookup6());

    dns.lookup6("vertx.io", ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals("0:0:0:0:0:0:0:1", result);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookup() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(FakeDNSServer.testLookup(ip));

    dns.lookup("vertx.io", ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals(ip, result);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testLookupNonExisting() throws Exception {
    DnsClient dns = prepareDns(FakeDNSServer.testLookupNonExisting());
    dns.lookup("gfegjegjf.sg1", ar -> {
      DnsException cause = (DnsException)ar.cause();
      assertEquals(DnsResponseCode.NXDOMAIN, cause.code());
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testReverseLookupIpv4() throws Exception {
    String address = "10.0.0.1";
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(FakeDNSServer.testReverseLookup(ptr));

    dns.reverseLookup(address, ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals(ptr, result);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testReverseLookupIpv6() throws Exception {
    final String ptr = "ptr.vertx.io";

    DnsClient dns = prepareDns(FakeDNSServer.testReverseLookup(ptr));

    dns.reverseLookup("::1", ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals(ptr, result);
      testComplete();
    });
    await();
    dnsServer.stop();
  }

  @Test
  public void testUseInMultithreadedWorker() throws Exception {
    class MyVerticle extends AbstractVerticle {
      @Override
      public void start() {
        assertIllegalStateException(() -> vertx.createDnsClient(1234, "localhost"));
        testComplete();
      }
    }
    MyVerticle verticle = new MyVerticle();
    vertx.deployVerticle(verticle, new DeploymentOptions().setWorker(true).setMultiThreaded(true));
    await();
  }

  private DnsClient prepareDns(FakeDNSServer server) throws Exception {
    dnsServer = server;
    dnsServer.start();
    InetSocketAddress addr = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    return vertx.createDnsClient(addr.getPort(), addr.getAddress().getHostAddress());
  }

}
