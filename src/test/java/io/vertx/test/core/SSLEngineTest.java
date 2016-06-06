package io.vertx.test.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.net.OpenSSLEngineOptions;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class SSLEngineTest extends VertxTestBase {


  @Test
  public void testOpenSslOptions() {
    OpenSSLEngineOptions options = new OpenSSLEngineOptions();

    assertEquals(true, options.isSessionCacheEnabled());
    assertEquals(options, options.setSessionCacheEnabled(false));
    assertEquals(false, options.isSessionCacheEnabled());
  }

  @Test
  public void testCopyOpenSslOptions() {
    OpenSSLEngineOptions options = new OpenSSLEngineOptions();

    boolean sessionCacheEnabled = TestUtils.randomBoolean();
    options.setSessionCacheEnabled(sessionCacheEnabled);

    OpenSSLEngineOptions copy = new OpenSSLEngineOptions(options);

    assertEquals(sessionCacheEnabled, copy.isSessionCacheEnabled());
  }

  @Test
  public void testDefaultOpenSslOptionsJson() {
    OpenSSLEngineOptions def = new OpenSSLEngineOptions();
    OpenSSLEngineOptions json = new OpenSSLEngineOptions(def.toJson());
    assertEquals(def.isSessionCacheEnabled(), json.isSessionCacheEnabled());
  }

  @Test
  public void testOpenSslOptionsJson() {
    boolean sessionCacheEnabled = TestUtils.randomBoolean();

    JsonObject json = new JsonObject();
    json.put("sessionCacheEnabled", sessionCacheEnabled);

    OpenSSLEngineOptions optins = new OpenSSLEngineOptions(json);

    assertEquals(sessionCacheEnabled, optins.isSessionCacheEnabled());
  }
}
