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
package io.vertx.core.internal.http;

import io.vertx.core.Future;
import io.vertx.core.http.HttpClientResponse;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface HttpClientResponseInternal extends HttpClientResponse {

  /**
   * Sets a decoder for the content of this response, replacing the default decoder feeding the response handlers.
   * The decoder must be set from the response handler, before any chunk is received.
   *
   * @param decoder the decoder
   * @return the result of the decoder, failed when the decoder fails or is closed before the end of the content
   * @throws IllegalStateException when the content has already started
   */
  <T> Future<T> contentDecoder(ContentDecoder<T> decoder);

}
