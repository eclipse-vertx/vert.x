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

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Signing algorithm through Message Authentication Code (MAC).
 *
 * @author Paulo Lopes
 */
public class MacSigningAlgorithm extends SigningAlgorithm {

  private final SecretKey secretKey;

  public MacSigningAlgorithm(SecretKey secretKey) {
    this.secretKey = Objects.requireNonNull(secretKey);
  }

  @Override
  public String id() {
    try {
      return "" + mac().hashCode();
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {
      throw new VertxException(e);
    }
  }

  public Mac mac() throws InvalidKeyException, NoSuchAlgorithmException {
    Mac mac = Mac.getInstance(secretKey.getAlgorithm());
    mac.init(secretKey);
    return mac;
  }

  @Override
  public boolean canSign() {
    return true;
  }

  @Override
  public boolean canVerify() {
    return true;
  }

  @Override
  public String name() {
    return secretKey.getAlgorithm();
  }

  @Override
  public Signer signer() throws NoSuchAlgorithmException, InvalidKeyException {
    Mac mac = mac();
    return mac::doFinal;
  }

  @Override
  public Verifier verifier() throws GeneralSecurityException {
    Mac mac = mac();
    return (expected, payload) -> MessageDigest.isEqual(expected, mac.doFinal(payload));
  }
}
