/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.spi.observability;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;

/**
 * An HTTP request.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpRequest {

  /**
   * @return the stream id
   * @deprecated the id cannot be guaranteed to be a stable value, it cannot be used for correlation purpose
   */
  @Deprecated(forRemoval = true)
  int id();

  /**
   * @return the request URI
   */
  String uri();

  /**
   * @return the request absolute URI
   */
  String absoluteURI();

  /**
   * @return the request method
   */
  HttpMethod method();

  /**
   * @return the request headers
   */
  MultiMap headers();

  /**
   * @return the request remote peer address
   */
  SocketAddress remoteAddress();
}
