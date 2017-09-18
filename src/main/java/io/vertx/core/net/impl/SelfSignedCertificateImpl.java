/*
 * Copyright (c) 2011-2017 The original author or authors
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     https://www.eclipse.org/legal/epl-2.0/
 *
 *     The Apache License v2.0 is available at
 *     http://www.apache.org/licenses/LICENSE-2.0
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
