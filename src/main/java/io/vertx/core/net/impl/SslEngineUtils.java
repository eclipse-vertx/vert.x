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
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.PqcEnforcementPolicy;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 *
 */
public class SslEngineUtils {

  private static final Logger log = LoggerFactory.getLogger(SslEngineUtils.class);

  private static final List<String> PQ_COMPLIANT_GROUPS = Collections.unmodifiableList(Arrays.asList("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024"));
  private static final List<String> DEFAULT_KEY_EXCHANGE_GROUPS = Collections.unmodifiableList(Arrays.asList("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024", "X25519", "secp256r1", "x448",
    "secp384r1", "secp521r1"));
  private static final Set<String> PQ_COMPLIANT_GROUPS_UPPER;
  static {
    Set<String> upper = new HashSet<>();
    for (String g : PQ_COMPLIANT_GROUPS) {
      upper.add(g.toUpperCase());
    }
    PQ_COMPLIANT_GROUPS_UPPER = Collections.unmodifiableSet(upper);
  }

  public static List<String> getPqCompliantGroups() {
    return PQ_COMPLIANT_GROUPS;
  }
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
        if (groups == null || groups.isEmpty()) {
          log.debug("No key exchange groups list was specified, the default list " + PQ_COMPLIANT_GROUPS + " is used");
          return PQ_COMPLIANT_GROUPS;
        }
        if (!groups.stream().allMatch(g -> PQ_COMPLIANT_GROUPS_UPPER.contains(g.toUpperCase()))) {
          log.warn("PQC enforcement policy is STRICT: overriding key exchange groups " + groups + " with " + PQ_COMPLIANT_GROUPS);
          return PQ_COMPLIANT_GROUPS;
        }
        return groups;
      case CLIENT_NEGOTIATED:
        if (groups == null || groups.isEmpty()) {
          log.debug("No key exchange groups list was specified, the default list " + DEFAULT_KEY_EXCHANGE_GROUPS + " is used");
          return DEFAULT_KEY_EXCHANGE_GROUPS;
        }
        if (groups.stream().noneMatch(g -> PQ_COMPLIANT_GROUPS_UPPER.contains(g.toUpperCase()))) {
          log.debug("PQC enforcement policy is CLIENT_NEGOTIATED: prepending " + PQ_COMPLIANT_GROUPS + " to key exchange groups " + groups);
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
        applyNamedGroups(engine, groups);
      }
    } catch (Exception e) {
      log.error("Unable to apply key exchange groups: " + e.getMessage() + ", closing engine", e);
      engine.closeOutbound();
    }
  }

  public static void applyNamedGroups(SSLEngine engine, List<String> groups) {
    try {
      Method setNamedGroups = SSLParameters.class.getDeclaredMethod("setNamedGroups", String[].class);
      SSLParameters params = engine.getSSLParameters();
      setNamedGroups.invoke(params, (Object) groups.toArray(new String[0]));
      engine.setSSLParameters(params);
    } catch (Exception e) {
      log.warn("Cannot apply key exchange groups " + groups + " on JDK SSL engine: requires JDK 20+", e);
    }
  }
}
