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

import io.netty.util.internal.PlatformDependent;

import java.security.GeneralSecurityException;

/**
 * Provide thread safety for verification/signing operations using an instance per thread based on thread local.
 */
public class ThreadLocalSigningAlgorithm extends SigningAlgorithm {

  private final SigningAlgorithm algorithm;
  private final ThreadLocal<Signer> localSigner = new ThreadLocal<>() {
    @Override
    protected Signer initialValue() {
      try {
        return algorithm.signer();
      } catch (GeneralSecurityException e) {
        PlatformDependent.throwException(e);
        return null;
      }
    }
  };
  private final ThreadLocal<Verifier> localVerifier = new ThreadLocal<>() {
    @Override
    protected Verifier initialValue() {
      try {
        return algorithm.verifier();
      } catch (GeneralSecurityException e) {
        PlatformDependent.throwException(e);
        return null;
      }
    }
  };

  public ThreadLocalSigningAlgorithm(SigningAlgorithm algorithm) {
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
  public Signer signer() {
    return payload -> localSigner.get().sign(payload);
  }

  @Override
  public Verifier verifier() {
    return (signature, payload) -> localVerifier.get().verify(signature, payload);
  }
}
