/*
 * Copyright 2025 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */
package io.vertx.core.internal.digest;

import io.vertx.core.VertxException;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * Signing algorithm through Digital Signature.
 *
 * @author Paulo Lopes
 */
public class DigitalSigningAlgorithm extends SigningAlgorithm {

  public static DigitalSigningAlgorithm createPubKeySigningAlgorithm(String alg,
                                                                     PrivateKey privateKey,
                                                                     PublicKey publicKey,
                                                                     String id,
                                                                     Callable<Signature> signatureFactory,
                                                                     int length) {
    return new DigitalSigningAlgorithm(alg, privateKey, publicKey, id, signatureFactory, length);
  }

  private final String alg;
  private final PrivateKey privateKey;
  private final PublicKey publicKey;
  private final String id;
  private final Callable<Signature> signatureFactory;

  // the length of the signature. This is derived from the algorithm name
  // this will help to cope with signatures that are longer (yet valid) than
  // the expected result
  private final int length;

  private DigitalSigningAlgorithm(String alg, PrivateKey privateKey, PublicKey publicKey, String id, Callable<Signature> signatureFactory, int length) {
    this.privateKey = privateKey;
    this.publicKey = publicKey;
    this.alg = Objects.requireNonNull(alg);
    this.id = id;
    this.signatureFactory = signatureFactory;
    this.length = length;
  }

  public int length() {
    return length;
  }

  public PrivateKey privateKey() {
    return privateKey;
  }

  public PublicKey publicKey() {
    return publicKey;
  }

  public Signature signature() {
    try {
      return signatureFactory.call();
    } catch (Exception e) {
      throw new VertxException(e);
    }
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public boolean canSign() {
    return privateKey != null;
  }

  @Override
  public boolean canVerify() {
    return publicKey != null;
  }

  // TODO : make this compliant
  @Override
  public String name() {
    return alg;
  }

  @Override
  public Signer signer() throws GeneralSecurityException {
    if (privateKey == null) {
      throw new IllegalStateException("JWK doesn't contain secKey material");
    }
    Signature signature;
    try {
      signature = signatureFactory.call();
    } catch (Exception e) {
      throw new GeneralSecurityException(e);
    }
    return payload -> {
      signature.initSign(privateKey);
      signature.update(payload);
      return signature.sign();
    };
  }

  @Override
  public Verifier verifier() throws GeneralSecurityException {
    if (publicKey == null) {
      throw new IllegalStateException("JWK doesn't contain pubKey material");
    }
    Signature sig;
    try {
      sig = signatureFactory.call();
    } catch (Exception e) {
      throw new GeneralSecurityException(e);
    }
    return (signature, payload) -> {
      sig.initVerify(publicKey);
      sig.update(payload);
      if (signature.length < length) {
        // need to adapt the expectation to make the RSA? engine happy
        byte[] normalized = new byte[length];
        System.arraycopy(signature, 0, normalized, 0, signature.length);
        return sig.verify(normalized);
      } else {
        return sig.verify(signature);
      }
    };
  }
}
