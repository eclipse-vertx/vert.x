package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.core.http.impl.HttpUtils;
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
   * Create an arbitrary instance.
   *
   * @param host the host value
   * @param port the port value
   * @return the instance.
   */
  static HostAndPort create(String host, int port) {
    return new HostAndPortImpl(host, port);
  }

  /**
   * Parse an authority HTTP header, that is <i>host [':' port]</i>, according to
   * <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">rfc3986</a>.
   *
   * @param string the string to parse
   * @param schemePort the scheme port used when the optional port is not specified
   * @return the parsed authority or {@code null} when the {@code string} does not represent a valid authority.
   */
  static HostAndPort parseAuthority(String string, int schemePort) {
    return HostAndPortImpl.parseAuthority(string, schemePort);
  }

  /**
   * Create an instance with a valid {@code host} for a valid authority: the {@code host} must
   * match the <i>host</i> rule of <a href="https://datatracker.ietf.org/doc/html/rfc3986#appendix-A">rfc3986</a>.
   *
   * @param host the host portion
   * @param port the port
   * @return the instance
   */
  static HostAndPort authority(String host, int port) {
    if (!HttpUtils.isValidHostAuthority(host)) {
      throw new IllegalArgumentException("Invalid authority host portion: " + host);
    }
    return new HostAndPortImpl(host, port);
  }

  /**
   * Like {@link #authority(String, int)} without a port, {@code -1} is used instead.
   */
  static HostAndPort authority(String host) {
    return authority(host, -1);
  }

  /**
   * @return the host value
   */
  String host();

  /**
   * @return the port value or {@code -1} when not specified
   */
  int port();

  String toString(boolean ssl);

  default JsonObject toJson() {
    JsonObject json = new JsonObject().put("host", host());
    int port = port();
    if (port > 0) {
      json.put("port", port);
    }
    return json;
  }

}
