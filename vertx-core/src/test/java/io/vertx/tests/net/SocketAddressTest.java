package io.vertx.tests.net;

import io.netty.util.NetUtil;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.SocketAddress;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.net.InetAddress;
import java.net.InetSocketAddress;

import static io.vertx.test.core.TestUtils.assertIllegalArgumentException;
import static io.vertx.test.core.TestUtils.assertNullPointerException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class SocketAddressTest extends VertxTestBase {

  @Test
  public void testInetSocketAddressFromUnresolvedAddress() {
    SocketAddress addr = SocketAddress.inetSocketAddress(InetSocketAddress.createUnresolved("localhost", 8080));
    assertEquals("localhost", addr.host());
    assertEquals("localhost", addr.hostName());
    assertEquals(null, addr.hostAddress());
    assertEquals(8080, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromResolvedAddress() {
    InetSocketAddress expected = new InetSocketAddress("localhost", 8080);
    SocketAddress addr = SocketAddress.inetSocketAddress(expected);
    assertEquals("localhost", addr.host());
    assertEquals("localhost", addr.hostName());
    assertEquals(expected.getAddress().getHostAddress(), addr.hostAddress());
    assertEquals(8080, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressIpV4Address() throws Exception {
    InetAddress ip = InetAddress.getByAddress(new byte[]{127, 0, 0, 1});
    SocketAddress addr = SocketAddress.inetSocketAddress(new InetSocketAddress(ip, 8080));
    assertEquals("127.0.0.1", addr.host());
    assertNull(addr.hostName());
    assertEquals(ip.getHostAddress(), addr.hostAddress());
    assertEquals(8080, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressIpV6Address() {
    InetAddress ip = NetUtil.getByName("::1");
    SocketAddress addr = SocketAddress.inetSocketAddress(new InetSocketAddress(ip, 8080));
    assertEquals("0:0:0:0:0:0:0:1", addr.host());
    assertNull(addr.hostName());
    assertEquals(ip.getHostAddress(), addr.hostAddress());
    assertEquals(8080, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromHostName() {
    SocketAddress addr = SocketAddress.inetSocketAddress(8080, "localhost");
    assertEquals("localhost", addr.host());
    assertEquals("localhost", addr.hostName());
    assertNull(addr.hostAddress());
    assertEquals(8080, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromIpV4AddressHost() {
    SocketAddress addr = SocketAddress.inetSocketAddress(8080, "127.0.0.1");
    assertEquals("127.0.0.1", addr.host());
    assertEquals(null, addr.hostName());
    assertEquals("127.0.0.1", addr.hostAddress());
    assertEquals(8080, addr.port());
    assertFalse(addr.isDomainSocket());
    assertTrue(addr.isInetSocket());
  }

  @Test
  public void testInetSocketAddressFromIpV6AddressHost() {
    SocketAddress addr = SocketAddress.inetSocketAddress(8080, "::1");
    assertEquals("::1", addr.host());
    assertEquals(null, addr.hostName());
    assertEquals("0:0:0:0:0:0:0:1", addr.hostAddress());
    assertEquals(8080, addr.port());
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

  @Test
  public void testFromJson() {
    assertNull(SocketAddress.fromJson(new JsonObject()));
    assertNull(SocketAddress.fromJson(new JsonObject().put("host", "the-host")));
    SocketAddress hostAndPort = SocketAddress.fromJson(new JsonObject().put("host", "the-host").put("port", 4));
    assertTrue(hostAndPort.isInetSocket());
    assertEquals("the-host", hostAndPort.host());
    assertEquals(4, hostAndPort.port());
    hostAndPort = SocketAddress.fromJson(new JsonObject().put("path", "/path"));
    assertTrue(hostAndPort.isDomainSocket());
    assertEquals("/path", hostAndPort.path());
    hostAndPort = SocketAddress.fromJson(new JsonObject().put("host", "the-host").put("port", -4));
    assertEquals("the-host", hostAndPort.host());
    assertEquals(-4, hostAndPort.port());
  }

  @Test
  public void testToJson() {
    assertEquals(new JsonObject().put("host", "the-host").put("port", 4), SocketAddress.inetSocketAddress(4, "the-host").toJson());
    assertEquals(new JsonObject().put("path", "/path"), SocketAddress.domainSocketAddress("/path").toJson());
    assertEquals(new JsonObject().put("host", "the-host").put("port", -4), SocketAddress.sharedRandomPort(4, "the-host").toJson());
  }
}
