package io.vertx.core.http.impl;

import io.vertx.core.http.HttpVersion;
import io.vertx.core.net.ClientSSLOptions;
import io.vertx.core.net.ProxyOptions;

import java.util.List;

public class HttpConnectParams {

  public HttpConnectParams(List<HttpVersion> protocols,
                           ClientSSLOptions sslOptions,
                           ProxyOptions proxyOptions,
                           boolean ssl) {
    this(protocols, sslOptions, proxyOptions, ssl, false);
  }

  public HttpConnectParams(List<HttpVersion> protocols,
                           ClientSSLOptions sslOptions,
                           ProxyOptions proxyOptions,
                           boolean ssl,
                           boolean forwardProxy) {
    this.protocols = protocols;
    this.sslOptions = sslOptions;
    this.proxyOptions = proxyOptions;
    this.ssl = ssl;
    this.forwardProxy = forwardProxy;
  }

  public final List<HttpVersion> protocols;
  public final ClientSSLOptions sslOptions;
  public final ProxyOptions proxyOptions;

  public final boolean ssl;

  public final boolean forwardProxy;

}
