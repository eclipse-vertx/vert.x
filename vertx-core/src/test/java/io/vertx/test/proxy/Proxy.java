/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.test.proxy;

import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.ProxyType;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.time.Duration;

public class Proxy implements TestRule {

  private ProxyBase<?> proxy;

  public String lastUri() {
    return proxy == null ? null : proxy.getLastUri();
  }

  public MultiMap lastRequestHeaders() {
    return proxy == null ? null : proxy.getLastRequestHeaders();
  }

  public HttpMethod lastMethod() {
    return proxy == null ? null : proxy.getLastMethod();
  }

  public int port() {
    return proxy == null ? -1 : proxy.port();
  }

  public ProxyType type() {
    if (proxy instanceof HttpProxy) {
      return ProxyType.HTTP;
    } else if (proxy instanceof SocksProxy) {
      return ProxyType.SOCKS5;
    } else if (proxy instanceof Socks4Proxy) {
      return ProxyType.SOCKS4;
    } else {
      return null;
    }
  }

  public Proxy forceUri(String uri) {
    proxy.setForceUri(uri);
    return this;
  }

  public Proxy successDelayMillis(Duration delay) {
    proxy.successDelayMillis(delay.toMillis());
    return this;
  }

  private class ProxyLifecycle extends Statement {

    private final String username;
    private final ProxyKind kind;
    private final Statement statement;
    private final String hosts;

    public ProxyLifecycle(String username, ProxyKind kind, String hosts, Statement statement) {
      this.username = username;
      this.kind = kind;
      this.statement = statement;
      this.hosts = hosts;
    }

    @Override
    public void evaluate() throws Throwable {

      VertxOptions options = new VertxOptions();
      if (hosts != null && !hosts.isEmpty()) {
        options.setAddressResolverOptions(new AddressResolverOptions().setHostsValue(Buffer.buffer(hosts)));
      }
      Vertx vertx = Vertx.vertx(options);
      if (kind == ProxyKind.HTTP) {
        proxy = new HttpProxy();
      } else if (kind == ProxyKind.SOCKS5) {
        proxy = new SocksProxy();
      } else {
        proxy = new Socks4Proxy();
      }
      if (!username.isEmpty()) {
        proxy.username(username);
      }
      proxy.start(vertx);
      try {
        statement.evaluate();
        proxy.stop();
      } finally {
        vertx.close().await();
      }
    }
  }

  @Override
  public Statement apply(Statement statement, Description description) {
    Statement result = statement;
    WithProxy repeat = description.getAnnotation(WithProxy.class);
    if (repeat != null ) {
      StringBuilder hosts = new StringBuilder();
      for (String host : repeat.localhosts()) {
        if (hosts.length() > 0) {
          hosts.append(System.lineSeparator());
        }
        hosts.append("127.0.0.1 ").append(host);
      }
      result = new ProxyLifecycle(repeat.username(), repeat.kind(), hosts.toString(), statement);
    }
    return result;
  }
}
