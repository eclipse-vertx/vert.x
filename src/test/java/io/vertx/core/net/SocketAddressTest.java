package io.vertx.core.net;

import io.netty.util.NetUtil;
import io.vertx.core.http.HttpTestBase;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;

public class SocketAddressTest extends VertxTestBase {

  @Test
  public void testInetSocketAddressFromUnresolvedAddress() {
    SocketAddress addr = SocketAddress.inetSocketAddress(InetSocketAddress.createUnresolved("localhost", HttpTestBase.DEFAULT_HTTP_PORT));
    assertEquals("localhost", addr.host());
    assertEquals("localhost", addr.hostName());
    assertEquals(null, addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromResolvedAddress() {
    InetSocketAddress expected = new InetSocketAddress("localhost", HttpTestBase.DEFAULT_HTTP_PORT);
    SocketAddress addr = SocketAddress.inetSocketAddress(expected);
    assertEquals("localhost", addr.host());
    assertEquals("localhost", addr.hostName());
    assertEquals(expected.getAddress().getHostAddress(), addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressIpV4Address() throws Exception {
    InetAddress ip = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
    SocketAddress addr = SocketAddress.inetSocketAddress(new InetSocketAddress(ip, HttpTestBase.DEFAULT_HTTP_PORT));
    assertEquals("127.0.0.1", addr.host());
    assertNull(addr.hostName());
    assertEquals(ip.getHostAddress(), addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressIpV6Address() {
    InetAddress ip = NetUtil.getByName("::1");
    SocketAddress addr = SocketAddress.inetSocketAddress(new InetSocketAddress(ip, HttpTestBase.DEFAULT_HTTP_PORT));
    assertEquals("0:0:0:0:0:0:0:1", addr.host());
    assertNull(addr.hostName());
    assertEquals(ip.getHostAddress(), addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromHostName() {
    SocketAddress addr = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "localhost");
    assertEquals("localhost", addr.host());
    assertEquals("localhost", addr.hostName());
    assertNull(addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromIpV4AddressHost() {
    SocketAddress addr = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "127.0.0.1");
    assertEquals("127.0.0.1", addr.host());
    assertEquals(null, addr.hostName());
    assertEquals("127.0.0.1", addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromIpV6AddressHost() {
    SocketAddress addr = SocketAddress.inetSocketAddress(HttpTestBase.DEFAULT_HTTP_PORT, "::1");
    assertEquals("::1", addr.host());
    assertEquals(null, addr.hostName());
    assertEquals("0:0:0:0:0:0:0:1", addr.hostAddress());
    assertEquals(HttpTestBase.DEFAULT_HTTP_PORT, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testDomainSocketAddress() {
    SocketAddress addr = SocketAddress.domainSocketAddress("/foo");
    assertEquals("/foo", addr.path());
    assertNull(addr.host());
    assertNull(addr.hostAddress());
    assertNull(addr.hostName());
    assertEquals(-1, addr.port());
    assertTrue(addr.isDomainSocket());
    assertFalse(addr.isInetSocket());
  }

  @Test
  public void testSocketAddress() throws Exception {
    assertNullPointerException(() -> SocketAddress.domainSocketAddress(null));
    assertNullPointerException(() -> SocketAddress.inetSocketAddress(0, null));
    assertIllegalArgumentException(() -> SocketAddress.inetSocketAddress(0, ""));
    assertIllegalArgumentException(() -> SocketAddress.inetSocketAddress(-1, "someHost"));
    assertIllegalArgumentException(() -> SocketAddress.inetSocketAddress(65536, "someHost"));
  }
}
