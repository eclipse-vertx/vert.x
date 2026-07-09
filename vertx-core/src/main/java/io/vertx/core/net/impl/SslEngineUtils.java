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
package io.vertx.core.net.impl;

import io.netty.handler.ssl.ReferenceCountedOpenSslEngine;
import io.netty.internal.tcnative.SSL;
import io.vertx.core.impl.JdkDependent;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.PqcEnforcementPolicy;

import javax.net.ssl.SSLEngine;
import java.util.ArrayList;
import java.util.List;

/**
 * Pre Java 21 implementation: named groups not supported.
 */
public class SslEngineUtils {

  private static final Logger log = LoggerFactory.getLogger(SslEngineUtils.class);

  private static final List<String> PQ_COMPLIANT_GROUPS = List.of("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024");
  private static final List<String> DEFAULT_KEY_EXCHANGE_GROUPS = List.of("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024", "X25519", "secp256r1", "x448",
    "secp384r1", "secp521r1");

  /**
   * Resolve the effective key exchange groups based on the PQC enforcement policy.
   * Called once at startup to avoid per-connection computation and logging.
   */
  public static List<String> resolveKeyExchangeGroups(List<String> groups, PqcEnforcementPolicy pqcPolicy) {
    if (pqcPolicy == null) {
      pqcPolicy = PqcEnforcementPolicy.RELAXED;
    }
    switch (pqcPolicy) {
      case STRICT:
        if (groups != null && !groups.isEmpty()) {
          if (!(PQ_COMPLIANT_GROUPS.containsAll(groups))) {
            log.warn("PQC enforcement policy is STRICT: overriding key exchange groups " + groups + " with " + PQ_COMPLIANT_GROUPS);
          }
        }
        return PQ_COMPLIANT_GROUPS;
      case CLIENT_NEGOTIATED:
        if (groups == null || groups.isEmpty()) {
          log.warn("No key exchange groups list was specified, a default list containing X25519MLKEM768 is selected");
          return DEFAULT_KEY_EXCHANGE_GROUPS;
        }
        if (groups.stream().noneMatch(PQ_COMPLIANT_GROUPS::contains)) {
          log.warn("PQC enforcement policy is CLIENT_NEGOTIATED: prepending " + PQ_COMPLIANT_GROUPS + " to key exchange groups " + groups);
          List<String> result = new ArrayList<>(groups.size() + 1);
          result.addAll(PQ_COMPLIANT_GROUPS);
          result.addAll(groups);
          return result;
        }
        return groups;
      case RELAXED:
      default:
        return groups;
    }
  }

  public static void applyKeyExchangeGroups(SSLEngine engine, List<String> groups) {
    try {
      if (engine instanceof ReferenceCountedOpenSslEngine) {
        long sslPtr = ((ReferenceCountedOpenSslEngine) engine).sslPointer();
        boolean success = SSL.setCurvesList(sslPtr, String.join(":", groups));
        if (!success) {
          log.error("Failed to set key exchange groups " + groups + " on SSL instance, closing engine");
          engine.closeOutbound();
        }
      } else {
        JdkDependent.applyNamedGroups(engine, groups);
      }
    } catch (Exception e) {
      log.error("Unable to apply key exchange groups: " + e.getMessage() + ", closing engine");
      engine.closeOutbound();
    }
  }
}
