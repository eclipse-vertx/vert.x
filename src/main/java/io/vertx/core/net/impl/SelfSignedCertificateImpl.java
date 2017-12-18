/*
 * Copyright (c) 2017 Red Hat, Inc. and/or its affiliates.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net.impl;

import io.vertx.core.VertxException;
import io.vertx.core.net.*;

import java.security.cert.CertificateException;

/**
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
public class SelfSignedCertificateImpl implements SelfSignedCertificate {

  private final io.netty.handler.ssl.util.SelfSignedCertificate certificate;

  public SelfSignedCertificateImpl() {
    try {
      certificate = new io.netty.handler.ssl.util.SelfSignedCertificate();
    } catch (CertificateException e) {
      throw new VertxException(e);
    }
  }

  public SelfSignedCertificateImpl(String fqdn) {
    try {
      certificate = new io.netty.handler.ssl.util.SelfSignedCertificate(fqdn);
    } catch (CertificateException e) {
      throw new VertxException(e);
    }
  }

  @Override
  public PemKeyCertOptions keyCertOptions() {
    return new PemKeyCertOptions()
      .setKeyPath(privateKeyPath())
      .setCertPath(certificatePath());
  }

  @Override
  public PemTrustOptions trustOptions() {
    return new PemTrustOptions().addCertPath(certificatePath());
  }

  @Override
  public String privateKeyPath() {
    return certificate.privateKey().getAbsolutePath();
  }

  @Override
  public String certificatePath() {
    return certificate.certificate().getAbsolutePath();
  }

  @Override
  public void delete() {
    certificate.delete();
  }
}
