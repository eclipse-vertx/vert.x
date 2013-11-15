/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */
package vertx.tests.core.dns;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Handler;
import org.vertx.java.core.dns.*;
import org.vertx.java.testframework.TestClientBase;
import org.vertx.testtools.TestDnsServer;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.List;

/**
 * @author <a href="mailto:nmaurer@redhat.com">Norman Maurer</a>
 */
public class DnsTestClient extends TestClientBase {
  // bytes representation of ::1
  private static final byte[] IP6_BYTES = new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1};

  private TestDnsServer dnsServer;
  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  @Override
  public void stop() {
    super.stop();
    if (dnsServer != null) {
      dnsServer.stop();
    }
  }

  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(TestDnsServer.testResolveA(ip));

    dns.resolveA("vertx.io", new Handler<AsyncResult<List<Inet4Address>>>() {
      @Override
      public void handle(AsyncResult<List<Inet4Address>> event) {
        tu.checkThread();
        List<Inet4Address> result = event.result();

        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert(ip.equals(result.get(0).getHostAddress()));
        tu.testComplete();
      }
    });

  }

  public void testResolveAAAA() throws Exception {
    DnsClient dns = prepareDns(TestDnsServer.testResolveAAAA("::1"));

    dns.resolveAAAA("vertx.io", new Handler<AsyncResult<List<Inet6Address>>>() {
      @Override
      public void handle(AsyncResult<List<Inet6Address>> event) {
        tu.checkThread();
        List<Inet6Address> result = event.result();
        tu.azzert(result != null);
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);

        tu.azzert(Arrays.equals(IP6_BYTES, result.get(0).getAddress()));
        tu.testComplete();
      }
    });
  }

  public void testResolveMX() throws Exception {
    final String mxRecord = "mail.vertx.io";
    final int prio = 10;
    DnsClient dns = prepareDns(TestDnsServer.testResolveMX(prio, mxRecord));

    dns.resolveMX("vertx.io", new Handler<AsyncResult<List<MxRecord>>>() {
      @Override
      public void handle(AsyncResult<List<MxRecord>> event) {
        tu.checkThread();
        List<MxRecord> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(1 == result.size());
        MxRecord record = result.get(0);
        tu.azzert(record.priority() == prio);
        tu.azzert(mxRecord.equals(record.name()));
        tu.testComplete();
      }
    });

  }

  public void testResolveTXT() throws Exception {
    final String txt = "vertx is awesome";
    DnsClient dns = prepareDns(TestDnsServer.testResolveTXT(txt));

    dns.resolveTXT("vertx.io", new Handler<AsyncResult<List<String>>>() {
      @Override
      public void handle(AsyncResult<List<String>> event) {
        tu.checkThread();
        List<String> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert(txt.equals(result.get(0)));
        tu.testComplete();
      }
    });

  }

  public void testResolveNS() throws Exception {
    final String ns = "ns.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testResolveNS(ns));

    dns.resolveNS("vertx.io", new Handler<AsyncResult<List<String>>>() {
      @Override
      public void handle(AsyncResult<List<String>> event) {
        tu.checkThread();
        List<String> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);
        tu.azzert(ns.equals(result.get(0)));
        tu.testComplete();
      }
    });

  }

  public void testResolveCNAME() throws Exception {
    final String cname = "cname.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testResolveCNAME(cname));

    dns.resolveCNAME("vertx.io", new Handler<AsyncResult<List<String>>>() {
      @Override
      public void handle(AsyncResult<List<String>> event) {
        tu.checkThread();
        List<String> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);

        String record = result.get(0);

        tu.azzert(!record.isEmpty());
        tu.azzert(cname.equals(record));
        tu.testComplete();
      }
    });
  }

  public void testResolvePTR() throws Exception {
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testResolvePTR(ptr));

    dns.resolvePTR("10.0.0.1.in-addr.arpa", new Handler<AsyncResult<String>>() {
      @Override
      public void handle(AsyncResult<String> event) {
        tu.checkThread();
        String result = event.result();
        tu.azzert(result != null);

        tu.azzert(ptr.equals(result));
        tu.testComplete();
      }
    });
  }


  public void testResolveSRV() throws Exception {
    final int priority = 10;
    final int weight = 1;
    final int port = 80;
    final String target = "vertx.io";

    DnsClient dns = prepareDns(TestDnsServer.testResolveSRV(priority, weight, port, target));

    dns.resolveSRV("vertx.io", new Handler<AsyncResult<List<SrvRecord>>>() {
      @Override
      public void handle(AsyncResult<List<SrvRecord>> event) {
        tu.checkThread();
        List<SrvRecord> result = event.result();
        tu.azzert(!result.isEmpty());
        tu.azzert(result.size() == 1);

        SrvRecord record = result.get(0);

        tu.azzert(priority == record.priority());
        tu.azzert(weight == record.weight());
        tu.azzert(port == record.port());
        tu.azzert(target.equals(record.target()));

        tu.testComplete();
      }
    });
  }

  public void testLookup4() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(TestDnsServer.testLookup4(ip));

    dns.lookup4("vertx.io", new Handler<AsyncResult<Inet4Address>>() {
      @Override
      public void handle(AsyncResult<Inet4Address> event) {
        tu.checkThread();
        InetAddress result = event.result();
        tu.azzert(result != null);
        tu.azzert(ip.equals(result.getHostAddress()));
        tu.testComplete();
      }
    });
  }

  public void testLookup6() throws Exception {
    DnsClient dns = prepareDns(TestDnsServer.testLookup6());

    dns.lookup6("vertx.io", new Handler<AsyncResult<Inet6Address>>() {
      @Override
      public void handle(AsyncResult<Inet6Address> event) {
        tu.checkThread();
        Inet6Address result = event.result();
        tu.azzert(result != null);
        tu.azzert(Arrays.equals(IP6_BYTES, result.getAddress()));
        tu.testComplete();
      }
    });
  }

  public void testLookup() throws Exception {
    final String ip = "10.0.0.1";
    DnsClient dns = prepareDns(TestDnsServer.testLookup(ip));

    dns.lookup("vertx.io", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        tu.checkThread();
        InetAddress result = event.result();
        tu.azzert(result != null);
        tu.azzert(ip.equals(result.getHostAddress()));
        tu.testComplete();
      }
    });
  }

  public void testLookupNonExisting() throws Exception {
    DnsClient dns = prepareDns(TestDnsServer.testLookupNonExisting());
    dns.lookup("gfegjegjf.sg1", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        tu.checkThread();
        DnsException cause = (DnsException) event.cause();
        tu.azzert(cause.code() == DnsResponseCode.NXDOMAIN);
        tu.testComplete();
      }
    });
  }

  public void testReverseLookupIpv4() throws Exception {
    final byte[] address = InetAddress.getByName("10.0.0.1").getAddress();
    final String ptr = "ptr.vertx.io";
    DnsClient dns = prepareDns(TestDnsServer.testReverseLookup(ptr));

    dns.reverseLookup("10.0.0.1", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        tu.checkThread();
        InetAddress result = event.result();
        tu.azzert(result != null);
        tu.azzert(result instanceof Inet4Address);
        tu.azzert(ptr.equals(result.getHostName()));
        tu.azzert(Arrays.equals(address, result.getAddress()));
        tu.testComplete();
      }
    });
  }


  public void testReverseLookupIpv6() throws Exception {
    final byte[] address = InetAddress.getByName("::1").getAddress();
    final String ptr = "ptr.vertx.io";

    DnsClient dns = prepareDns(TestDnsServer.testReverseLookup(ptr));

    dns.reverseLookup("::1", new Handler<AsyncResult<InetAddress>>() {
      @Override
      public void handle(AsyncResult<InetAddress> event) {
        tu.checkThread();
        InetAddress result = event.result();
        tu.azzert(result != null);
        tu.azzert(result instanceof Inet6Address);
        tu.azzert(ptr.equals(result.getHostName()));
        tu.azzert(Arrays.equals(address, result.getAddress()));
        tu.testComplete();
      }
    });
  }

  private DnsClient prepareDns(TestDnsServer server) throws Exception {
    dnsServer = server;
    dnsServer.start();
    InetSocketAddress addr = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();
    return vertx.createDnsClient(addr);
  }
}
