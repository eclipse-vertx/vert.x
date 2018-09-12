/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.tls;

import io.vertx.core.net.JksOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;

import java.util.function.Supplier;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public interface Trust<T extends TrustOptions> extends Supplier<T> {
  Trust<TrustOptions> NONE = () -> null;
  Trust<JksOptions> SERVER_JKS = () -> new JksOptions().setPath("tls/client-truststore.jks").setPassword("wibble");
  Trust<JksOptions> CLIENT_JKS = () -> new JksOptions().setPath("tls/server-truststore.jks").setPassword("wibble");
  Trust<PfxOptions> SERVER_PKCS12 = () -> new PfxOptions().setPath("tls/client-truststore.p12").setPassword("wibble");
  Trust<PfxOptions> CLIENT_PKCS12 = () -> new PfxOptions().setPath("tls/server-truststore.p12").setPassword("wibble");
  Trust<PemTrustOptions> SERVER_PEM = () -> new PemTrustOptions().addCertPath("tls/server-cert.pem");
  Trust<PemTrustOptions> CLIENT_PEM = () -> new PemTrustOptions().addCertPath("tls/client-cert.pem");
  Trust<JksOptions> SERVER_JKS_ROOT_CA = () -> new JksOptions().setPath("tls/client-truststore-root-ca.jks").setPassword("wibble");
  Trust<PfxOptions> SERVER_PKCS12_ROOT_CA = () -> new PfxOptions().setPath("tls/client-truststore-root-ca.p12").setPassword("wibble");
  Trust<PemTrustOptions> SERVER_PEM_ROOT_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
  Trust<PemTrustOptions> CLIENT_PEM_ROOT_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
  Trust<PemTrustOptions> SERVER_PEM_ROOT_CA_AND_OTHER_CA = () -> new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem").addCertPath("tls/other-ca/ca-cert.pem");
  Trust<JksOptions> SNI_JKS_HOST1 = () -> new JksOptions().setPath("tls/sni-truststore-host1.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST2 = () -> new JksOptions().setPath("tls/sni-truststore-host2.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST3 = () -> new JksOptions().setPath("tls/sni-truststore-host3.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST4 = () -> new JksOptions().setPath("tls/sni-truststore-host4.jks").setPassword("wibble");
  Trust<JksOptions> SNI_JKS_HOST5 = () -> new JksOptions().setPath("tls/sni-truststore-host5.jks").setPassword("wibble");
  Trust<TrustOptions> SNI_SERVER_ROOT_CA_AND_OTHER_CA_1 = () -> new JksOptions().setPath("tls/server-truststore-root-ca-host2.jks").setPassword("wibble");
  Trust<TrustOptions> SNI_SERVER_ROOT_CA_AND_OTHER_CA_2 = () -> new JksOptions().setPath("tls/server-truststore-root-ca-host3.jks").setPassword("wibble");
  Trust<TrustOptions> SNI_SERVER_ROOT_CA_FALLBACK = () -> new JksOptions().setPath("tls/server-truststore-root-ca-fallback.jks").setPassword("wibble");
  Trust<TrustOptions> SNI_SERVER_OTHER_CA_FALLBACK = () -> new JksOptions().setPath("tls/server-truststore-other-ca-fallback.jks").setPassword("wibble");
}
