package io.vertx.core.dns;

import io.vertx.core.Vertx;
import io.vertx.core.VertxException;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.impl.DohClientImpl;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.FakeDNSServer;
import io.vertx.test.netty.TestLoggerFactory;
import org.apache.directory.server.dns.messages.DnsMessage;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static io.vertx.test.core.TestUtils.assertNullPointerException;

/**
 * @author <a href="mailto:zolfaghari19@gmail.com">imz87</a>
 */
public class DohTest extends VertxTestBase {

  private FakeDNSServer dnsServer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dnsServer = new FakeDNSServer(true, vertx);
    dnsServer.start();
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
  }

  @Test
  public void testDefaultDnsClientWithOptions() throws Exception {
    int port = 53529;
    VertxOptions vertxOptions = new VertxOptions();
    vertxOptions.getAddressResolverOptions().addServer("127.0.0.1" + ":" + port);

    Vertx vertxWithFakeDns = Vertx.vertx(vertxOptions);

    FakeDNSServer dnsServer2 = new FakeDNSServer(true, vertxWithFakeDns);
    dnsServer2.port(port);
    dnsServer2.start();

    Function<Vertx, DnsClient> vertxDnsClientFunction =
      vertx -> vertx.createDnsClient(new DnsClientOptions().setSsl(true).setPort(dnsServer2.port()).setHost(dnsServer2.ipAddress()));

    DnsClient dnsClient = vertxDnsClientFunction.apply(vertxWithFakeDns);

    final String ip = "10.0.0.1";
    dnsServer2.testLookup4(ip);

    dnsClient.lookup4("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      testComplete();
    }));
    await();
    vertxWithFakeDns.close();
    dnsServer2.stop();
  }

  @Test
  public void testResolveA() throws Exception {
    final String ip = "10.0.0.1";
    dnsServer.testResolveA(ip);
    DnsClient dns = prepareDns();

    dns.resolveA("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ip, result.get(0));
      ((DohClientImpl) dns).inProgressQueries(num -> {
        assertEquals(0, (int)num);
        testComplete();
      });
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
    FakeDNSServer dnsServer2 = new FakeDNSServer(true, vertx);
    dnsServer2.ipAddress("::1");
    dnsServer2.start();
    dnsServer2.testResolveA(ip).ipAddress("::1");
    // force the fake dns server to Ipv6
    DnsClient dns =
      vertx.createDnsClient(new DnsClientOptions().setSsl(true).setPort(dnsServer2.port()).setHost(dnsServer2.ipAddress()));
    dns.resolveA("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ip, result.get(0));
      ((DohClientImpl) dns).inProgressQueries(num -> {
        assertEquals(0, (int) num);
        testComplete();
      });
    }));
    await();
    dnsServer2.stop();
  }

  @Test
  public void testResolveAAAA() throws Exception {
    dnsServer.testResolveAAAA("::1");
    DnsClient dns = prepareDns();

    dns.resolveAAAA("vertx.io", onSuccess(result -> {
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

    dns.resolveMX("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      MxRecord record = result.get(0);
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
    dns.resolveTXT("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(txt, result.get(0));
      testComplete();
    }));
    await();
  }

  @Test
  public void testResolveNS() throws Exception {
    final String ns = "ns.vertx.io";
    dnsServer.testResolveNS(ns);
    DnsClient dns = prepareDns();

    dns.resolveNS("vertx.io", onSuccess(result -> {
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

    dns.resolveCNAME("vertx.io", onSuccess(result -> {
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

    dns.resolvePTR("10.0.0.1.in-addr.arpa", onSuccess(result -> {
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
    final String target = "vertx.io";

    dnsServer.testResolveSRV(priority, weight, port, target);
    DnsClient dns = prepareDns();

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
    dnsServer.testLookup4(ip);
    DnsClient dns = prepareDns();
    dns.lookup4("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      DnsMessage msg = dnsServer.pollMessage();
      assertTrue(msg.isRecursionDesired());
      testComplete();
    }));
    await();
  }

  @Test
  public void testLookup6() throws Exception {
    dnsServer.testLookup6();
    DnsClient dns = prepareDns();

    dns.lookup6("vertx.io", onSuccess(result -> {
      assertEquals("0:0:0:0:0:0:0:1", result);
      testComplete();
    }));
    await();
  }

  @Test
  public void testLookup() throws Exception {
    String ip = "10.0.0.1";
    dnsServer.testLookup(ip);
    DnsClient dns = prepareDns();

    dns.lookup("vertx.io", onSuccess(result -> {
      assertEquals(ip, result);
      testComplete();
    }));
    await();
  }

  @Test
  public void testTimeout() throws Exception {
    DnsClient dns = vertx.createDnsClient(new DnsClientOptions().setSsl(true).setHost("localhost").setPort(10000).setQueryTimeout(1));

    dns.lookup("vertx.io", onFailure(result -> {
      assertEquals(VertxException.class, result.getClass());
      assertEquals("DNS query timeout for vertx.io.", result.getMessage());
      ((DohClientImpl) dns).inProgressQueries(num -> {
        assertEquals(0, (int)num);
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testLookupNonExisting() throws Exception {
    dnsServer.testLookupNonExisting();
    DnsClient dns = prepareDns();
    dns.lookup("gfegjegjf.sg1", ar -> {
      DnsException cause = (DnsException)ar.cause();
      assertEquals(DnsResponseCode.NXDOMAIN, cause.code());
      testComplete();
    });
    await();
  }

  @Test
  public void testReverseLookupIpv4() throws Exception {
    String address = "10.0.0.1";
    String ptr = "ptr.vertx.io";
    dnsServer.testReverseLookup(ptr);
    DnsClient dns = prepareDns();

    dns.reverseLookup(address, onSuccess(result -> {
      assertEquals(ptr, result);
      testComplete();
    }));
    await();
  }

  @Test
  public void testReverseLookupIpv6() throws Exception {
    String ptr = "ptr.vertx.io";
    dnsServer.testReverseLookup(ptr);
    DnsClient dns = prepareDns();

    dns.reverseLookup("::1", onSuccess(result -> {
      assertEquals(ptr, result);
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

    dns.lookup4("vertx.io", onSuccess(result -> {
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
      .onComplete(ar -> {
        assertTrue(ar.failed());
        testComplete();
      });
    await();
  }

  private TestLoggerFactory testLogging(DnsClientOptions options) {
    final String ip = "10.0.0.1";
    dnsServer.testResolveA(ip);
    return TestUtils.testLogging(() -> {
      try {
        prepareDns(options)
          .resolveA(ip, fut -> {
            testComplete();
          });
        await();
      } catch (Exception e) {
        fail(e);
      }
    });
  }

  @Test
  public void testLogActivity() throws Exception {
    TestLoggerFactory factory = testLogging(new DnsClientOptions().setSsl(true).setLogActivity(true));
    assertTrue(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testDoNotLogActivity() throws Exception {
    TestLoggerFactory factory = testLogging(new DnsClientOptions().setSsl(true).setLogActivity(false));
    assertFalse(factory.hasName("io.netty.handler.logging.LoggingHandler"));
  }

  @Test
  public void testRecursionDesired() throws Exception {
    final String ip = "10.0.0.1";

    dnsServer.testResolveA(ip);
    DnsClient dns = prepareDns(new DnsClientOptions().setSsl(true).setRecursionDesired(true));
    dns.resolveA("vertx.io", onSuccess(result -> {
      assertFalse(result.isEmpty());
      assertEquals(1, result.size());
      assertEquals(ip, result.get(0));
      DnsMessage msg = dnsServer.pollMessage();
      assertTrue(msg.isRecursionDesired());
      ((DohClientImpl) dns).inProgressQueries(num -> {
        assertEquals(0, (int)num);
        testComplete();
      });
    }));
    await();
  }

  @Test
  public void testClose() throws Exception {
    waitFor(2);
    String ip = "10.0.0.1";
    RecordStore store = dnsServer.testResolveA(ip).store();
    CountDownLatch latch1 = new CountDownLatch(1);
    CountDownLatch latch2 = new CountDownLatch(1);
    dnsServer.store(question -> {
      latch1.countDown();
      try {
        latch2.await(10, TimeUnit.SECONDS);
      } catch (Exception e) {
        fail(e);
      }
      return store.getRecords(question);
    });
    DnsClient dns = prepareDns(new DnsClientOptions().setSsl(true).setQueryTimeout(15000));
    dns
      .resolveA("vertx.io")
      .onComplete(onFailure(timeout -> {
        assertTrue(timeout.getMessage().contains("closed"));
        complete();
      }));
    awaitLatch(latch1);
    dns.close().onComplete(onSuccess(v -> {
      complete();
      latch2.countDown();
    }));
    await();
  }

  private DnsClient prepareDns() throws Exception {
    return prepareDns(new DnsClientOptions().setSsl(true).setQueryTimeout(15000));
  }

  private DnsClient prepareDns(DnsClientOptions options) throws Exception {
    return vertx.createDnsClient(new DnsClientOptions(options).setPort(dnsServer.port()).setHost(dnsServer.ipAddress()));
  }
}
