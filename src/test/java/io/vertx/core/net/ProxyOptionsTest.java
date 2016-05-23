/**
 * 
 */
package io.vertx.core.net;

import org.junit.Test;

import static org.junit.Assert.*;

import io.vertx.core.json.JsonObject;

/**
 * @author <a href="http://oss.lehmann.cx/">Alexander Lehmann</a>
 *
 */
public class ProxyOptionsTest {

  @Test
  public void test() {
    ProxyOptions options = new ProxyOptions();
    options.setProxyHost("somehost")
      .setProxyPort(12345)
      .setProxyUsername("user")
      .setProxyPassword("secret")
      .setProxyType(ProxyType.HTTP);

    assertEquals("somehost", options.getProxyHost());
    assertEquals(12345, options.getProxyPort());
    assertEquals("user", options.getProxyUsername());
    assertEquals("secret", options.getProxyPassword());
    assertEquals("HTTP", options.getProxyType().toString());
  }

  @Test
  public void testConstructorOther() {
    ProxyOptions options = new ProxyOptions(new ProxyOptions().setProxyHost("somehost"));

    assertEquals("somehost", options.getProxyHost());
  }

  @Test
  public void testConstructorJson() {
    ProxyOptions options = new ProxyOptions(new JsonObject("{\"proxyHost\":\"somehost\"}"));

    assertEquals("somehost", options.getProxyHost());
  }

  @Test
  public void testClone() {
    ProxyOptions options = new ProxyOptions().setProxyHost("hostname").clone();

    assertEquals("hostname", options.getProxyHost());
  }

  @Test(expected = NullPointerException.class)
  public void testProxyTypeNull() {
    new ProxyOptions().setProxyType(null);
  }

}
