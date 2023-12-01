package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.impl.HostAndPortImpl;

/**
 * A combination of host and port.
 */
@DataObject
public interface HostAndPort {

  static HostAndPort fromJson(JsonObject json) {
    int port = json.getInteger("port", -1);
    String host = json.getString("host");
    if (host != null) {
      return HostAndPort.create(host, port);
    }
    return null;
  }

  /**
   * Create an instance.
   *
   * @param host the host value
   * @param port the port value
   * @return the instance.
   */
  static HostAndPort create(String host, int port) {
    return new HostAndPortImpl(host, port);
  }

  /**
   * @return the host value
   */
  String host();

  /**
   * @return the port value
   */
  int port();

  default JsonObject toJson() {
    JsonObject json = new JsonObject().put("host", host());
    int port = port();
    if (port > 0) {
      json.put("port", port);
    }
    return json;
  }

}
