package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;

/**
 * Configures a {@link TCPSSLOptions} to use the JDK ssl engine implementation.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@DataObject
public class JdkSSLEngineOptions extends SSLEngineOptions {

  private static Boolean jdkAlpnAvailable;

  /**
   * @return if alpn support is available via the JDK SSL engine
   */
  public static synchronized boolean isAlpnAvailable() {
    if (jdkAlpnAvailable == null) {
      boolean available = false;
      try {
        // Always use bootstrap class loader.
        JdkSSLEngineOptions.class.getClassLoader().loadClass("sun.security.ssl.ALPNExtension");
        available = true;
      } catch (Exception ignore) {
        // alpn-boot was not loaded.
      } finally {
        jdkAlpnAvailable = available;
      }
    }
    return jdkAlpnAvailable;
  }

  public JdkSSLEngineOptions() {
  }

  public JdkSSLEngineOptions(JsonObject json) {
  }

  public JdkSSLEngineOptions(JdkSSLEngineOptions that) {
  }

  public JsonObject toJson() {
    return new JsonObject();
  }

  @Override
  public int hashCode() {
    return 0;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JdkSSLEngineOptions)) return false;
    return true;
  }

  @Override
  public SSLEngineOptions clone() {
    return new JdkSSLEngineOptions();
  }
}
