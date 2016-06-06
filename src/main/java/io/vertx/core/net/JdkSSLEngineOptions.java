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

  public JdkSSLEngineOptions() {
  }

  public JdkSSLEngineOptions(JsonObject json) {
  }

  public JdkSSLEngineOptions(JdkSSLEngineOptions that) {
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
}
