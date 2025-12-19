package io.vertx.tests.endpoint;

import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.vertx.core.Future;
import io.vertx.core.VertxOptions;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.endpoint.EndpointBuilder;
import io.vertx.core.spi.endpoint.EndpointResolver;
import io.vertx.test.core.TestUtils;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.MockDnsServer;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.*;

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
    resolver = (EndpointResolver) ((VertxInternal)vertx).nameResolver().endpointResolver(vertx);
  }

  public void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }

  @Test
  public void testResolveMultipleAddresses() {
    Future<List<SocketAddress>> fut = resolver.resolve(SocketAddress.inetSocketAddress(8080, nameToResolve), new EndpointBuilder<List<SocketAddress>, SocketAddress>() {
      @Override
      public EndpointBuilder<List<SocketAddress>, SocketAddress> addServer(SocketAddress server, String key) {
        List<SocketAddress> list = new ArrayList<>();
        list.add(server);
        return new EndpointBuilder<>() {
          @Override
          public EndpointBuilder<List<SocketAddress>, SocketAddress> addServer(SocketAddress server, String key) {
            list.add(server);
            return this;
          }
          @Override
          public List<SocketAddress> build() {
            return list;
          }
        };
      }

      @Override
      public List<SocketAddress> build() {
        throw new IllegalStateException();
      }
    });
    fut.onComplete(onSuccess(state -> {
      assertEquals(2, state.size());
      SocketAddress addr1 = state.get(0);
      SocketAddress addr2 = state.get(1);
      assertEquals("127.0.0.1", addr1.host());
      assertEquals(8080, addr1.port());
      assertEquals("127.0.0.2", addr2.host());
      assertEquals(8080, addr2.port());
      testComplete();
    }));
    await();
  }
}
