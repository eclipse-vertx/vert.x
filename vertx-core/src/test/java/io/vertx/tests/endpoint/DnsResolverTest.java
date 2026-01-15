package io.vertx.tests.endpoint;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.MockDnsServer;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class DnsResolverTest extends VertxTestBase {

  private String nameToResolve = TestUtils.randomAlphaString(8) + ".com";
  private MockDnsServer dnsServer;

  @Override
  protected VertxOptions getOptions() {
    InetSocketAddress dnsAddr = dnsServer.localAddress();
    return new VertxOptions().setAddressResolverOptions(new AddressResolverOptions()
      .setServers(Collections.singletonList(dnsAddr.getAddress().getHostAddress() + ":" + dnsAddr.getPort())));
  }

  public void setUp() throws Exception {
    dnsServer = new MockDnsServer();
    dnsServer.start();
    dnsServer.store(questionRecord -> {
      Set<DnsRecord> set = new LinkedHashSet<>();
      if (nameToResolve.equals(questionRecord.name())) {
        for (int i = 0; i < 2; i++) {
          String ip = "127.0.0." + (i + 1);
          set.add(new DnsRecord() {
            @Override
            public String name() {
              return nameToResolve;
            }

            @Override
            public DnsRecordType type() {
              return DnsRecordType.A;
            }

            @Override
            public int dnsClass() {
              return 1;
            }

            @Override
            public long timeToLive() {
              return 100;
            }

            /*@Override
            public String get(String id) {
              if (id.equals(DnsAttribute.IP_ADDRESS)) {
                return ip;
              }
              return null;
            }*/
          });
        }
      }
      return set;
    });
    super.setUp();
  }

  public void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }
}
