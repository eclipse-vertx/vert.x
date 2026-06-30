/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;

/**
 * Policy for enforcing post-quantum cryptography (PQC) key exchange.
 */
@VertxGen
@Unstable
public enum PqcEnforcementPolicy {
  /**
   * No PQC enforcement. Key exchange groups are used as-is if specified.
   * If no groups are specified, the SSL engine negotiates normally.
   */
  RELAXED,
  /**
   * PQC is enforced on the server side but clients that do not support PQC are tolerated.
   * X25519MLKEM768 is prepended to the key exchange groups if not already present.
   * If PQC is not available at runtime, the application fails to start with a VertxException:
   * "PQC enforcement policy CLIENT_NEGOTIATED requires X25519MLKEM768 but the configured SSL engine does not support it"
   */
  CLIENT_NEGOTIATED,
  /**
   * PQC is strictly enforced. Both server and client must support PQC.
   * The key exchange groups are replaced with only X25519MLKEM768.
   * If PQC is not available at runtime, the application fails to start with a VertxException:
   * "PQC enforcement policy STRICT requires X25519MLKEM768 but the configured SSL engine does not support it"
   * Non-PQC clients will fail the TLS handshake.
   */
  STRICT
}
