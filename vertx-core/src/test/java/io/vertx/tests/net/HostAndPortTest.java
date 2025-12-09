package io.vertx.tests.net;

import io.vertx.core.http.impl.UriParser;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.HostAndPort;
import io.vertx.core.net.impl.HostAndPortImpl;
import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.*;

public class HostAndPortTest {

  @Test
  public void testParseIPLiteral() {
    Assert.assertEquals(-1, UriParser.parseIPLiteral("", 0, 0));
    assertEquals(-1, UriParser.parseIPLiteral("[", 0, 1));
    assertEquals(-1, UriParser.parseIPLiteral("[]", 0, 2));
    assertEquals(3, UriParser.parseIPLiteral("[0]", 0, 3));
    assertEquals(-1, UriParser.parseIPLiteral("[0", 0, 2));
  }

  @Test
  public void testParseDecOctet() {
    assertEquals(-1, UriParser.parseDecOctet("", 0, 0));
    assertEquals(1, UriParser.parseDecOctet("0", 0, 1));
    assertEquals(1, UriParser.parseDecOctet("9", 0, 1));
    assertEquals(1, UriParser.parseDecOctet("01", 0, 2));
    assertEquals(2, UriParser.parseDecOctet("19", 0, 2));
    assertEquals(3, UriParser.parseDecOctet("192", 0, 3));
    assertEquals(3, UriParser.parseDecOctet("1234", 0, 4));
    assertEquals(-1, UriParser.parseDecOctet("256", 0, 3));
  }

  @Test
  public void testParseIPV4Address() {
    assertEquals(-1, UriParser.parseIPv4Address("0.0.0", 0, 5));
    assertEquals(-1, UriParser.parseIPv4Address("0.0.0#0", 0, 7));
    assertEquals(7, UriParser.parseIPv4Address("0.0.0.0", 0, 7));
    assertEquals(11, UriParser.parseIPv4Address("192.168.0.0", 0, 11));
    assertEquals(-1, UriParser.parseIPv4Address("011.168.0.0", 0, 11));
    assertEquals(-1, UriParser.parseIPv4Address("10.0.0.1.nip.io", 0, 15));
    assertEquals(-1, UriParser.parseIPv4Address("10.0.0.1.nip.io", 0, 9));
    assertEquals(8, UriParser.parseIPv4Address("10.0.0.1.nip.io", 0, 8));
    assertEquals(-1, UriParser.parseIPv4Address("10.0.0.1:", 0, 9));
    assertEquals(8, UriParser.parseIPv4Address("10.0.0.1:0", 0, 10));
  }

  @Test
  public void testParseRegName() {
    assertEquals(5, UriParser.parseRegName("abcdef", 0, 5));
    assertEquals(5, UriParser.parseRegName("abcdef:1234", 0, 5));
    assertEquals(11, UriParser.parseRegName("example.com", 0, 11));
    assertEquals(14, UriParser.parseRegName("example-fr.com", 0, 14));
    assertEquals(15, UriParser.parseRegName("10.0.0.1.nip.io", 0, 15));
  }

  @Test
  public void testParseHost() {
    assertEquals(14, UriParser.parseHost("example-fr.com", 0, 14));
    assertEquals(5, UriParser.parseHost("[0::]", 0, 5));
    assertEquals(7, UriParser.parseHost("0.0.0.0", 0, 7));
    assertEquals(8, UriParser.parseHost("10.0.0.1.nip.io", 0, 8));
    assertEquals(15, UriParser.parseHost("10.0.0.1.nip.io", 0, 15));
    assertEquals(8, UriParser.parseHost("10.0.0.1:8080", 0, 15));
  }

  @Test
  public void testParseHostAndPort() {
    assertHostAndPort("10.0.0.1.nip.io", -1, "10.0.0.1.nip.io");
    assertHostAndPort("10.0.0.1.nip.io", 8443, "10.0.0.1.nip.io:8443");
    assertHostAndPort("127.0.0.1", 8080, "127.0.0.1:8080");
    assertHostAndPort("example.com", 8080, "example.com:8080");
    assertHostAndPort("example.com", -1, "example.com");
    assertHostAndPort("0.1.2.3", -1, "0.1.2.3");
    assertHostAndPort("[0::]", -1, "[0::]");
    assertHostAndPort("", -1, "");
    assertHostAndPort("", 8080, ":8080");
    assertNull(HostAndPortImpl.parseAuthority("/", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("/"));
    assertNull(HostAndPortImpl.parseAuthority("10.0.0.1:x", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("10.0.0.1:x"));
  }

  @Test
  public void testParseInvalid() {
    assertHostAndPort("localhost", 65535, "localhost:65535");
    assertNull(HostAndPortImpl.parseAuthority("localhost:65536", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("localhost:65536"));
    assertNull(HostAndPortImpl.parseAuthority("localhost:8080a", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("localhost:8080a"));
    assertNull(HostAndPortImpl.parseAuthority("http://localhost:8080", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("http://localhost:8080"));
    assertNull(HostAndPortImpl.parseAuthority("^", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("^"));
    assertNull(HostAndPortImpl.parseAuthority("bücher.de", -1));
    assertFalse(HostAndPortImpl.isValidAuthority("bücher.de"));
  }

  private void assertHostAndPort(String expectedHost, int expectedPort, String actual) {
    HostAndPortImpl hostAndPort = HostAndPortImpl.parseAuthority(actual, -1);
    assertNotNull(hostAndPort);
    assertTrue(HostAndPortImpl.isValidAuthority(actual));
    assertEquals(expectedHost, hostAndPort.host());
    assertEquals(expectedPort, hostAndPort.port());
  }

  @Test
  public void testFromJson() {
    assertNull(HostAndPort.fromJson(new JsonObject()));
    HostAndPort hostAndPort = HostAndPort.fromJson(new JsonObject().put("host", "the-host"));
    assertEquals("the-host", hostAndPort.host());
    assertEquals(-1, hostAndPort.port());
    hostAndPort = HostAndPort.fromJson(new JsonObject().put("host", "the-host").put("port", 4));
    assertEquals("the-host", hostAndPort.host());
    assertEquals(4, hostAndPort.port());
  }

  @Test
  public void testToJson() {
    assertEquals(new JsonObject().put("host", "the-host").put("port", 4), HostAndPort.create("the-host", 4).toJson());
    assertEquals(new JsonObject().put("host", "the-host"), HostAndPort.create("the-host", -1).toJson());
  }
}
