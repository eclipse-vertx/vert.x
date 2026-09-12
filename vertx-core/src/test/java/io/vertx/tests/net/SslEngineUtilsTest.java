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
package io.vertx.tests.net;

import io.vertx.core.net.PqcEnforcementPolicy;
import io.vertx.core.net.impl.SslEngineUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SslEngineUtilsTest {

  // The canonical PQ compliant groups defined in SslEngineUtils
  private static final List<String> PQ_COMPLIANT = List.of("X25519MLKEM768", "SecP256r1MLKEM768", "SecP384r1MLKEM1024");

  // --- STRICT policy ---

  @Test
  public void testStrictPolicyAcceptsAllLowercasePqGroups() {
    List<String> groups = List.of("x25519mlkem768", "secp256r1mlkem768", "secp384r1mlkem1024");
    assertEquals(groups, SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.STRICT));
  }

  @Test
  public void testStrictPolicyAcceptsAllUppercasePqGroups() {
    List<String> groups = List.of("X25519MLKEM768", "SECP256R1MLKEM768", "SECP384R1MLKEM1024");
    assertEquals(groups, SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.STRICT));
  }

  @Test
  public void testStrictPolicyAcceptsMixedCasePqGroups() {
    List<String> groups = List.of("X25519Mlkem768", "secP256r1MLKEM768", "SecP384R1mlkem1024");
    assertEquals(groups, SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.STRICT));
  }

  @Test
  public void testStrictPolicyOverridesGroupsContainingNonPqEntry() {
    // Mixed: one PQ (wrong case) + one non-PQ — the non-PQ entry triggers override
    List<String> groups = List.of("x25519mlkem768", "X25519");
    List<String> result = SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.STRICT);
    assertEquals(PQ_COMPLIANT, result);
  }

  @Test
  public void testStrictPolicyOverridesNonPqGroups() {
    List<String> groups = List.of("X25519", "secp256r1");
    List<String> result = SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.STRICT);
    assertEquals(PQ_COMPLIANT, result);
  }

  // --- CLIENT_NEGOTIATED policy ---

  @Test
  public void testClientNegotiatedDoesNotPrependWhenLowercasePqGroupPresent() {
    List<String> groups = List.of("x25519mlkem768", "X25519");
    assertEquals(groups, SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.CLIENT_NEGOTIATED));
  }

  @Test
  public void testClientNegotiatedDoesNotPrependWhenUppercasePqGroupPresent() {
    List<String> groups = List.of("X25519MLKEM768", "secp256r1");
    assertEquals(groups, SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.CLIENT_NEGOTIATED));
  }

  @Test
  public void testClientNegotiatedDoesNotPrependWhenMixedCasePqGroupPresent() {
    List<String> groups = List.of("secP384r1MLKEM1024", "X25519");
    assertEquals(groups, SslEngineUtils.resolveKeyExchangeGroups(groups, PqcEnforcementPolicy.CLIENT_NEGOTIATED));
  }

  @Test
  public void testClientNegotiatedPrependsWhenNoPqGroupPresent() {
    List<String> nonPq = List.of("X25519", "secp256r1");
    List<String> result = SslEngineUtils.resolveKeyExchangeGroups(nonPq, PqcEnforcementPolicy.CLIENT_NEGOTIATED);
    // PQ groups prepended, original groups preserved at the end
    assertTrue(result.size() == nonPq.size() + PQ_COMPLIANT.size());
    assertEquals(PQ_COMPLIANT, result.subList(0, PQ_COMPLIANT.size()));
    assertEquals(nonPq, result.subList(PQ_COMPLIANT.size(), result.size()));
  }
}
