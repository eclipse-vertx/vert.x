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

/**
 * Represents the version of the HTTP protocol.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
@VertxGen
public enum HttpVersion {
  HTTP_1_0("http/1.0"), HTTP_1_1("http/1.1"), HTTP_2("h2");

  private final String alpnName;

  HttpVersion(String alpnName) {
    this.alpnName = alpnName;
  }

  /**
   * @return the protocol name for Application-Layer Protocol Negotiation (ALPN).
   */
  public String alpnName() {
    return alpnName;
  }
}
