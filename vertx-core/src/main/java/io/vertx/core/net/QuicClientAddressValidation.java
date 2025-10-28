/*
 * Copyright (c) 2011-2025 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.core.net;

/**
 * Quic client address validation on the server.
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum QuicClientAddressValidation {

  /**
   * The server won't perform any validation (no Retry packet is emitted).
   */
  NONE,

  /**
   * The server performs basic token validation without any crypto.
   */
  BASIC,

  /**
   * The server performs validation using cryptography.
   */
  CRYPTO

}
