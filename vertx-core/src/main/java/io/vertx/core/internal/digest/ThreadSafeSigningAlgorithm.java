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
package io.vertx.core.internal.digest;

import java.security.GeneralSecurityException;

/**
 * Provide thread safety for verification/signing operations using mutual exclusion.
 */
public class ThreadSafeSigningAlgorithm extends SigningAlgorithm {

  private final SigningAlgorithm algorithm;

  public ThreadSafeSigningAlgorithm(SigningAlgorithm algorithm) {
    this.algorithm = algorithm;
  }

  @Override
  public SigningAlgorithm safe() {
    return this;
  }

  @Override
  public SigningAlgorithm unwrap() {
    return algorithm.unwrap();
  }

  @Override
  public String name() {
    return algorithm.name();
  }

  @Override
  public String id() {
    return algorithm.id();
  }

  @Override
  public boolean canSign() {
    return algorithm.canSign();
  }

  @Override
  public boolean canVerify() {
    return algorithm.canVerify();
  }

  @Override
  public Signer signer() throws GeneralSecurityException {
    Signer signer = algorithm.signer();
    return payload -> {
      synchronized (signer) {
        return signer.sign(payload);
      }
    };
  }

  @Override
  public Verifier verifier() throws GeneralSecurityException {
    Verifier verifier = algorithm.verifier();
    return (signature, payload) -> {
      synchronized (verifier) {
        return verifier.verify(signature, payload);
      }
    };
  }
}
