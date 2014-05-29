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

import org.junit.Test;
import org.vertx.java.core.dns.*;
import org.vertx.testtools.TestDnsServer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class DNSTest extends VertxTestBase {

  // bytes representation of ::1
  private static final byte[] IP6_BYTES = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

  private TestDnsServer dnsServer;

  @Test
  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(TestDnsServer.testResolveA(ip));

    dns.resolveA("vertx.io", ar -> {
      List<Inet4Address> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ip, result.get(0).getHostAddress());
      testComplete();
    });
    await();
  }

  @Test
  public void testResolveAAAA() throws Exception {
    DnsClient dns = prepareDns(TestDnsServer.testResolveAAAA("::1"));

    dns.resolveAAAA("vertx.io", ar -> {
      List<Inet6Address> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertTrue(Arrays.equals(IP6_BYTES, result.get(0).getAddress()));
      testComplete();
    });
    await();
  }

  @Test
  public void testResolveMX() throws Exception {
    final String mxRecord = "mail.vertx.io";
    final int prio = 10;
    DnsClient dns = prepareDns(TestDnsServer.testResolveMX(prio, mxRecord));

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
  }

  @Test
  public void testResolveTXT() throws Exception {
    final String txt = "vertx is awesome";
    DnsClient dns = prepareDns(TestDnsServer.testResolveTXT(txt));

    dns.resolveTXT("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(txt, result.get(0));
      testComplete();
    });
    await();
  }

  @Test
  public void testResolveNS() throws Exception {
    final String ns = "ns.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testResolveNS(ns));

    dns.resolveNS("vertx.io", ar -> {
      List<String> result = ar.result();
      assertNotNull(result);
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ns, result.get(0));
      testComplete();
    });
    await();
  }

  @Test
  public void testResolveCNAME() throws Exception {
    final String cname = "cname.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testResolveCNAME(cname));

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
  }

  @Test
  public void testResolvePTR() throws Exception {
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testResolvePTR(ptr));

    dns.resolvePTR("10.0.0.1.in-addr.arpa", ar -> {
      String result = ar.result();
      assertNotNull(result);
      assertEquals(ptr, result);
      testComplete();
    });
    await();
  }

  @Test
  public void testResolveSRV() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;
    final String target = "vertx.io";

    DnsClient dns = prepareDns(TestDnsServer.testResolveSRV(priority, weight, port, target));

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
  }

  @Test
  public void testLookup4() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(TestDnsServer.testLookup4(ip));

    dns.lookup4("vertx.io", ar -> {
      InetAddress result = ar.result();
      assertNotNull(result);
      assertEquals(ip, result.getHostAddress());
      testComplete();
    });
    await();
  }

  @Test
  public void testLookup6() throws Exception {
    DnsClient dns = prepareDns(TestDnsServer.testLookup6());

    dns.lookup6("vertx.io", ar -> {
      Inet6Address result = ar.result();
      assertNotNull(result);
      assertTrue(Arrays.equals(IP6_BYTES, result.getAddress()));
      testComplete();
    });
    await();
  }

  @Test
  public void testLookup() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(TestDnsServer.testLookup(ip));

    dns.lookup("vertx.io", ar -> {
      InetAddress result = ar.result();
      assertNotNull(result);
      assertEquals(ip, result.getHostAddress());
      testComplete();
    });
    await();
  }

  @Test
  public void testLookupNonExisting() throws Exception {
    DnsClient dns = prepareDns(TestDnsServer.testLookupNonExisting());
    dns.lookup("gfegjegjf.sg1", ar -> {
      DnsException cause = (DnsException)ar.cause();
      assertEquals(DnsResponseCode.NXDOMAIN, cause.code());
      testComplete();
    });
    await();
  }

  @Test
  public void testReverseLookupIpv4() throws Exception {
    final byte[] address = InetAddress.getByName("10.0.0.1").getAddress();
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testReverseLookup(ptr));

    dns.reverseLookup("10.0.0.1", ar -> {
      InetAddress result = ar.result();
      assertNotNull(result);
      assertTrue(result instanceof Inet4Address);
      assertEquals(ptr, result.getHostName());
      assertTrue(Arrays.equals(address, result.getAddress()));
      testComplete();
    });
    await();
  }

  @Test
  public void testReverseLookupIpv6() throws Exception {
    final byte[] address = InetAddress.getByName("::1").getAddress();
    final String ptr = "ptr.vertx.io";

    DnsClient dns = prepareDns(TestDnsServer.testReverseLookup(ptr));

    dns.reverseLookup("::1", ar -> {
      InetAddress result = ar.result();
      assertNotNull(result);
      assertTrue(result instanceof Inet6Address);
      assertEquals(ptr, result.getHostName());
      assertTrue(Arrays.equals(address, result.getAddress()));
      testComplete();
    });
    await();
  }

  private DnsClient prepareDns(TestDnsServer server) throws Exception {
    dnsServer = server;
    dnsServer.start();
    InetSocketAddress addr = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    return vertx.createDnsClient(addr);
  }

}
