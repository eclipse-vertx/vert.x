package io.vertx.test.it;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.impl.VertxInternal;
import io.vertx.test.fakedns.FakeDNSServer;
import org.junit.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

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

    String old = System.getProperty("os.name");
    System.setProperty("os.name", "Windows 10");
    Class<?> utils;
    Boolean isWindows;
    try {
      utils = Vertx.class.getClassLoader().loadClass("io.vertx.core.impl.Utils");
      Method m = utils.getDeclaredMethod("isWindows");
      isWindows = (Boolean) m.invoke(null);
    } finally {
      System.setProperty("os.name", old);
    }
    assertTrue(isWindows);

    // Give
    dnsServer = FakeDNSServer.testResolveASameServer("127.0.0.1");
    dnsServer.start();
    dnsServerAddress = (InetSocketAddress) dnsServer.getTransports()[0].getAcceptor().getLocalAddress();

    VertxInternal vertx = (VertxInternal) Vertx.vertx(new VertxOptions().setAddressResolverOptions(
        new AddressResolverOptions().
            setHostsValue(Buffer.buffer("")).
            addServer(dnsServerAddress.getAddress().getHostAddress() + ":" + dnsServerAddress.getPort()).
            setOptResourceEnabled(false)
    ));
    CompletableFuture<Void> test = new CompletableFuture<>();
    vertx.resolveAddress("localhost", ar -> {
      if (ar.succeeded()) {
        test.complete(null);
      } else {
        test.completeExceptionally(ar.cause());
      }
    });
    test.get(10, TimeUnit.SECONDS);
  }

}
