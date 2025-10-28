/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.http;

/**
 * Subset of <a href="https://www.iana.org/assignments/tls-extensiontype-values/tls-extensiontype-values.xhtml#alpn-protocol-ids">ALPN protocol ID</a>
 * for HTTP applications.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum HttpProtocol {

  /**
   * HTTP/1.0
   */
  HTTP_1_0("http/1.0"),

  /**
   * HTTP/1.1
   */
  HTTP_1_1("http/1.1"),

  /**
   * HTTP/2 over TLS
   */
  H2("h2"),

  /**
   * HTTP/2 over TCP
   */
  H2C("h2c"),

  /**
   * HTTP/3
   */
  H3("h3");

  private final String id;

  HttpProtocol(String id) {
    this.id = id;
  }

  /**
   * @return the id of the protocol
   */
  public String id() {
    return id;
  }

  /**
   * @return the version of the protocol
   */
  public HttpVersion version() {
    return HttpVersion.fromProtocol(this);
  }

  /**
   * Provides the version of the given protocol {@code id}
   *
   * @param id the protocol id
   * @return the protocol of {@code null} when no id is matching
   */
  public static HttpProtocol fromId(String id) {
    switch (id) {
      case "http/1.0":
        return HTTP_1_0;
      case "http/1.1":
        return HTTP_1_1;
      case "h2":
        return H2;
      case "h2c":
        return H2C;
      case "h3":
        return H3;
      default:
        return null;
    }
  }
}
