/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.net;

import io.vertx.codegen.annotations.DataObject;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.net.impl.SelfSignedCertificateImpl;

/**
 * A self-signed certificate helper for testing and development purposes.
 * <p>
 * While it helps for testing and development, it should never ever be used in production settings.
 *
 * @author <a href="https://julien.ponge.org/">Julien Ponge</a>
 */
@DataObject
public interface SelfSignedCertificate {

  /**
   * Provides the {@link KeyCertOptions} RSA private key file in PEM format corresponding to the {@link #privateKeyPath()}
   *
   * @return a {@link PemKeyCertOptions} based on the generated certificate.
   */
  PemKeyCertOptions keyCertOptions();

  /**
   * Provides the {@link TrustOptions} X.509 certificate file in PEM format corresponding to the {@link #certificatePath()}
   *
   * @return a {@link PemTrustOptions} based on the generated certificate.
   */
  PemTrustOptions trustOptions();

  /**
   * Filesystem path to the RSA private key file in PEM format
   *
   * @return the absolute path to the private key.
   */
  String privateKeyPath();

  /**
   * Filesystem path to the X.509 certificate file in PEM format .
   *
   * @return the absolute path to the certificate.
   */
  String certificatePath();

  /**
   * Delete the private key and certificate files.
   */
  void delete();

  /**
   * Create a new {@code SelfSignedCertificate} instance.
   *
   * @return a new instance.
   */
  static SelfSignedCertificate create() {
    return new SelfSignedCertificateImpl();
  }

  /**
   * Create a new {@code SelfSignedCertificate} instance with a fully-qualified domain name,
   *
   * @param fqdn a fully qualified domain name.
   * @return a new instance.
   */
  static SelfSignedCertificate create(String fqdn) {
    return new SelfSignedCertificateImpl(fqdn);
  }
}
