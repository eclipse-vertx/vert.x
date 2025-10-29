package io.vertx.test.proxy;

public enum ProxyKind {

  /**
   * HTTP CONNECT ssl proxy
   */
  HTTP,

  /**
   * SOCKS4/4a tcp proxy
   */
  SOCKS4,

  /**
   * SOCSK5 tcp proxy
   */
  SOCKS5,

  HA

}
