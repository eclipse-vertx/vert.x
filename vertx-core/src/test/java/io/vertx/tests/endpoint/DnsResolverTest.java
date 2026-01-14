package io.vertx.tests.endpoint;

import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.FakeDNSServer;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;

import java.net.InetSocketAddress;
import java.util.*;

public class DnsResolverTest extends VertxTestBase {

  private String nameToResolve = TestUtils.randomAlphaString(8) + ".com";
  private FakeDNSServer dnsServer;

  @Override
  protected VertxOptions getOptions() {
    InetSocketAddress dnsAddr = dnsServer.localAddress();
    return new VertxOptions().setAddressResolverOptions(new AddressResolverOptions()
      .setServers(Collections.singletonList(dnsAddr.getAddress().getHostAddress() + ":" + dnsAddr.getPort())));
  }

  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer();
    dnsServer.start();
    dnsServer.store(questionRecord -> {
      Set<ResourceRecord> set = new LinkedHashSet<>();
      if (nameToResolve.equals(questionRecord.getDomainName())) {
        for (int i = 0;i < 2;i++) {
          String ip = "127.0.0." + (i + 1);
          set.add(new ResourceRecord() {
            @Override
            public String getDomainName() {
              return nameToResolve;
            }
            @Override
            public RecordType getRecordType() {
              return RecordType.A;
            }
            @Override
            public RecordClass getRecordClass() {
              return RecordClass.IN;
            }
            @Override
            public int getTimeToLive() {
              return 100;
            }
            @Override
            public String get(String id) {
              if (id.equals(DnsAttribute.IP_ADDRESS)) {
                return ip;
              }
              return null;
            }
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
