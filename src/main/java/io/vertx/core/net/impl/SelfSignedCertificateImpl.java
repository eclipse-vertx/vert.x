/*
 * Copyright (c) 2017 Red Hat, Inc. and/or its affiliates.
 * -------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
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
