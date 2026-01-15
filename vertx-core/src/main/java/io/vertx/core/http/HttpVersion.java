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

package io.vertx.core.http;

import io.vertx.codegen.annotations.VertxGen;

import java.util.List;

/**
 * Represents the version of the HTTP protocol.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public enum HttpVersion {

  HTTP_1_0("http/1.0", List.of(HttpProtocol.HTTP_1_0)),
  HTTP_1_1("http/1.1", List.of(HttpProtocol.HTTP_1_1)),
  HTTP_2("h2", List.of(HttpProtocol.H2, HttpProtocol.H2C)),
  HTTP_3("h3", List.of(HttpProtocol.H3));

  private final String alpnName;
  private final List<HttpProtocol> protocols;

  HttpVersion(String alpnName, List<HttpProtocol> protocols) {
    this.alpnName = alpnName;
    this.protocols = protocols;
  }

  /**
   * @return the protocol name for Application-Layer Protocol Negotiation (ALPN) of the secured version of this protocol version.
   */
  public String alpnName() {
    return alpnName;
  }

  /**
   * @return the associated Application-Layer Protocol Negotiation (ALPN) protocols.
   */
  public List<HttpProtocol> protocols() {
    return protocols;
  }

  /**
   * Provides the version of the given protocol {@code id}
   *
   * @param id the protocol id
   * @return the version of {@code null} when no id is matching
   */
  public static HttpVersion fromAlpnName(String id) {
    switch (id) {
      case "http/1.0":
        return HTTP_1_0;
      case "http/1.1":
        return HTTP_1_1;
      case "h2":
      case "h2c":
        return HTTP_2;
      default:
        return null;
    }
  }

  /**
   * Provides the version of the given protocol {@code id}
   *
   * @param id the protocol id
   * @return the version of {@code null} when no id is matching
   */
  public static HttpVersion fromProtocol(HttpProtocol protocol) {
    switch (protocol) {
      case HTTP_1_0:
        return HTTP_1_0;
      case HTTP_1_1:
        return HTTP_1_1;
      case H2:
      case H2C:
        return HTTP_2;
      case H3:
        return HTTP_3;
      default:
        return null;
    }
  }
}
