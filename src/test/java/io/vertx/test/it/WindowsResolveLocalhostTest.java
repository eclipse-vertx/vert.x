package io.vertx.test.it;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Integration test that tricks Vert.x to make it behave like on Windows 10
 * to test the localhost un-resolution feature of this OS.
 * This test must run isolated from other tests.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class WindowsResolveLocalhostTest {

  private FakeDNSServer dnsServer;
  private InetSocketAddress dnsServerAddress;

  @Test
  public void testResolveLocalhostOnWindows() throws Exception {

    InetAddress localhost = InetAddress.getLocalHost();

    // Set a dns resolver that won't resolve localhost
    dnsServer = FakeDNSServer.testResolveASameServer("127.0.0.1");
    dnsServer.start();
    dnsServerAddress = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();

    // Test using the resolver API
    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            setHostsValue(Buffer.buffer("")).
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false)
    ));
    CompletableFuture<Void> test = new CompletableFuture<>();
    vertx.resolveAddress("localhost", ar -> {
      if (ar.succeeded()) {
        InetAddress resolved = ar.result();
        if (resolved.equals(localhost)) {
          test.complete(null);
        } else {
          test.completeExceptionally(new AssertionError("Unexpected localhost value " + resolved));
        }
      } else {
        test.completeExceptionally(ar.cause());
      }
    });
    test.get(10, TimeUnit.SECONDS);

    // Test using bootstrap
    CompletableFuture<Void> test2 = new CompletableFuture<>();
    NetServer server = vertx.createNetServer(new NetServerOptions().setPort(1234).setHost(localhost.getHostAddress()));
    server.connectHandler(so -> {
      so.write("hello").end();
    });
    server.listen(ar -> {
      if (ar.succeeded()) {
        test2.complete(null);
      } else {
        test2.completeExceptionally(ar.cause());
      }
    });
    test2.get(10, TimeUnit.SECONDS);

    CompletableFuture<Void> test3 = new CompletableFuture<>();
    NetClient client = vertx.createNetClient();
    client.connect(1234, "localhost", ar -> {
      if (ar.succeeded()) {
        test3.complete(null);
      } else {
        test3.completeExceptionally(ar.cause());
      }
    });
    test3.get(10, TimeUnit.SECONDS);
  }
}
