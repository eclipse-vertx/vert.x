package io.vertx.core.net.impl;

import org.junit.Test;

import static org.junit.Assert.*;

public class HostAndPortTest {

  @Test
  public void testParseIPLiteral() {
    assertEquals(-1, HostAndPortImpl.parseIPLiteral("", 0, 0));
    assertEquals(-1, HostAndPortImpl.parseIPLiteral("[", 0, 1));
    assertEquals(-1, HostAndPortImpl.parseIPLiteral("[]", 0, 2));
    assertEquals(3, HostAndPortImpl.parseIPLiteral("[0]", 0, 3));
    assertEquals(-1, HostAndPortImpl.parseIPLiteral("[0", 0, 2));
  }

  @Test
  public void testParseDecOctet() {
    assertEquals(-1, HostAndPortImpl.parseDecOctet("", 0, 0));
    assertEquals(1, HostAndPortImpl.parseDecOctet("0", 0, 1));
    assertEquals(1, HostAndPortImpl.parseDecOctet("9", 0, 1));
    assertEquals(1, HostAndPortImpl.parseDecOctet("01", 0, 2));
    assertEquals(2, HostAndPortImpl.parseDecOctet("19", 0, 2));
    assertEquals(3, HostAndPortImpl.parseDecOctet("192", 0, 3));
    assertEquals(3, HostAndPortImpl.parseDecOctet("1234", 0, 4));
    assertEquals(-1, HostAndPortImpl.parseDecOctet("256", 0, 3));
  }

  @Test
  public void testParseIPV4Address() {
    assertEquals(-1, HostAndPortImpl.parseIPv4Address("0.0.0", 0, 5));
    assertEquals(-1, HostAndPortImpl.parseIPv4Address("0.0.0#0", 0, 7));
    assertEquals(7, HostAndPortImpl.parseIPv4Address("0.0.0.0", 0, 7));
    assertEquals(11, HostAndPortImpl.parseIPv4Address("192.168.0.0", 0, 11));
    assertEquals(-1, HostAndPortImpl.parseIPv4Address("011.168.0.0", 0, 11));
    assertEquals(-1, HostAndPortImpl.parseIPv4Address("10.0.0.1.nip.io", 0, 15));
    assertEquals(-1, HostAndPortImpl.parseIPv4Address("10.0.0.1.nip.io", 0, 9));
    assertEquals(8, HostAndPortImpl.parseIPv4Address("10.0.0.1.nip.io", 0, 8));
    assertEquals(-1, HostAndPortImpl.parseIPv4Address("10.0.0.1:", 0, 9));
  }

  @Test
  public void testParseRegName() {
    assertEquals(5, HostAndPortImpl.parseRegName("abcdef", 0, 5));
    assertEquals(5, HostAndPortImpl.parseRegName("abcdef:1234", 0, 5));
    assertEquals(11, HostAndPortImpl.parseRegName("example.com", 0, 11));
    assertEquals(14, HostAndPortImpl.parseRegName("example-fr.com", 0, 14));
    assertEquals(15, HostAndPortImpl.parseRegName("10.0.0.1.nip.io", 0, 15));
  }

  @Test
  public void testParseHost() {
    assertEquals(14, HostAndPortImpl.parseHost("example-fr.com", 0, 14));
    assertEquals(5, HostAndPortImpl.parseHost("[0::]", 0, 5));
    assertEquals(7, HostAndPortImpl.parseHost("0.0.0.0", 0, 7));
    assertEquals(8, HostAndPortImpl.parseHost("10.0.0.1.nip.io", 0, 8));
    assertEquals(15, HostAndPortImpl.parseHost("10.0.0.1.nip.io", 0, 15));
  }

  @Test
  public void testParseHostAndPort() {
    assertHostAndPort("10.0.0.1.nip.io", -1, "10.0.0.1.nip.io");
    assertHostAndPort("10.0.0.1.nip.io", 8443, "10.0.0.1.nip.io:8443");
    assertHostAndPort("example.com", 8080, "example.com:8080");
    assertHostAndPort("example.com", -1, "example.com");
    assertHostAndPort("0.1.2.3", -1, "0.1.2.3");
    assertHostAndPort("[0::]", -1, "[0::]");
    assertHostAndPort("", -1, "");
    assertHostAndPort("", 8080, ":8080");
    assertNull(HostAndPortImpl.parseHostAndPort("/", -1));
    assertNull(HostAndPortImpl.parseHostAndPort("10.0.0.1:x", -1));
  }

  private void assertHostAndPort(String expectedHost, int expectedPort, String actual) {
    HostAndPortImpl hostAndPort = HostAndPortImpl.parseHostAndPort(actual, -1);
    assertNotNull(hostAndPort);
    assertEquals(expectedHost, hostAndPort.host());
    assertEquals(expectedPort, hostAndPort.port());
  }
}
