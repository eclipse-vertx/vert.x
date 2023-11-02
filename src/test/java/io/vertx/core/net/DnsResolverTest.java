package io.vertx.core.net;

import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.spi.resolver.address.AddressResolver;
import io.vertx.core.spi.resolver.address.Endpoint;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.FakeDNSServer;
import org.apache.directory.server.dns.messages.RecordClass;
import org.apache.directory.server.dns.messages.RecordType;
import org.apache.directory.server.dns.messages.ResourceRecord;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DnsResolverTest extends VertxTestBase {

  private FakeDNSServer dnsServer;
  private AddressResolver<SocketAddress, SocketAddress, List<Endpoint<SocketAddress>>> resolver;

  @Override
  protected VertxOptions getOptions() {
    InetSocketAddress dnsAddr = dnsServer.localAddress();
    return new VertxOptions().setAddressResolverOptions(new AddressResolverOptions()
      .setServers(Collections.singletonList(dnsAddr.getAddress().getHostAddress() + ":" + dnsAddr.getPort())));
  }

  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer();
    dnsServer.start();
    super.setUp();
    dnsServer.store(questionRecord -> {
      Set<ResourceRecord> set = new LinkedHashSet<>();
      if ("example.com".equals(questionRecord.getDomainName())) {
        for (int i = 0;i < 2;i++) {
          String ip = "127.0.0." + (i + 1);
          set.add(new ResourceRecord() {
            @Override
            public String getDomainName() {
              return "example.com";
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
    resolver = (AddressResolver) ((VertxInternal)vertx).hostnameResolver().resolver(vertx);
  }

  public void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }

  @Test
  public void testResolveMultipleAddresses() {
    Future<List<Endpoint<SocketAddress>>> fut = resolver.resolve(so -> () -> so, SocketAddress.inetSocketAddress(8080, "example.com"));
    fut.onComplete(onSuccess(state -> {
      assertEquals(2, state.size());
      SocketAddress addr1 = state.get(0).get();
      SocketAddress addr2 = state.get(1).get();
      assertEquals("127.0.0.1", addr1.host());
      assertEquals(8080, addr1.port());
      assertEquals("127.0.0.2", addr2.host());
      assertEquals(8080, addr2.port());
      testComplete();
    }));
    await();
  }
}
