package io.vertx.core.http;

import io.vertx.core.VertxOptions;
import io.vertx.core.VertxTest;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.test.core.VertxTestBase;
import io.vertx.test.fakedns.FakeDNSServer;
import org.apache.directory.server.dns.messages.*;
import org.apache.directory.server.dns.store.DnsAttribute;
import org.apache.directory.server.dns.store.RecordStore;
import org.junit.Assert;
import org.junit.Test;

import java.net.InetSocketAddress;
import java.util.*;

public class ResolvingHttpClientTest extends VertxTestBase {

  private FakeDNSServer dnsServer;
  private InetSocketAddress dnsServerAddress;

  @Override
  public void setUp() throws Exception {
    dnsServer = new FakeDNSServer();
    dnsServer.start();
    dnsServerAddress = dnsServer.localAddress();
    super.setUp();
  }

  @Override
  protected void tearDown() throws Exception {
    dnsServer.stop();
    super.tearDown();
  }

  @Override
  protected VertxOptions getOptions() {
    return super.getOptions().setAddressResolverOptions(getAddressResolverOptions());
  }

  private AddressResolverOptions getAddressResolverOptions() {
    return new AddressResolverOptions()
      .addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort());
  }

  @Test
  public void testResolve() throws Exception {
    int numServers = 2;
    List<HttpServer> servers = new ArrayList<>();
    for (int i = 0;i < numServers;i++) {
      int val = i;
      HttpServer server = vertx.createHttpServer().requestHandler(req -> {
        req.response().end("server-" + val);
      });
      servers.add(server);
      awaitFuture(server.listen(8080 + i, "localhost"));
    }
    dnsServer.store(question -> {
      Set<ResourceRecord> set = new HashSet<>();
      if (question.getDomainName().equals("example.com")) {
        for (int i = 0;i < numServers;i++) {
          ResourceRecordModifier rm = new ResourceRecordModifier();
          rm.setDnsClass(RecordClass.IN);
          rm.setDnsName("dns.vertx.io." + i);
          rm.setDnsTtl(100);
          rm.setDnsType(RecordType.SRV);
          rm.put(DnsAttribute.SERVICE_PRIORITY, String.valueOf(1));
          rm.put(DnsAttribute.SERVICE_WEIGHT, String.valueOf(1));
          rm.put(DnsAttribute.SERVICE_PORT, String.valueOf(8080 + i));
          rm.put(DnsAttribute.DOMAIN_NAME, "localhost");
          set.add(rm.getEntry());
        }
      }
      return set;
    });
    HttpClient client = vertx.createHttpClient();
    waitFor(numServers * 2);
    Set<String> responses = Collections.synchronizedSet(new HashSet<>());
    for (int i = 0;i < numServers * 2;i++) {
      client.request(HttpMethod.GET, 8080, "example.com", "/").compose(req -> req
        .send()
        .andThen(onSuccess(resp -> assertEquals(200, resp.statusCode())))
        .compose(HttpClientResponse::body)
      ).onComplete(onSuccess(v -> {
        responses.add(v.toString());
        complete();
      }));
    }
    await();
    Assert.assertEquals(new HashSet<>(Arrays.asList("server-0", "server-1")), responses);
  }
}
