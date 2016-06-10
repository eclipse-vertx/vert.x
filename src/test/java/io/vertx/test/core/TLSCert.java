/*
 * Copyright (c) 2011-2014 The original author or authors
 * ------------------------------------------------------
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
package io.vertx.test.core;

import io.netty.handler.codec.UnsupportedMessageTypeException;
import io.vertx.core.net.JksOptions;
import io.vertx.core.net.KeyCertOptions;
import io.vertx.core.net.PemKeyCertOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.core.net.PfxOptions;
import io.vertx.core.net.TrustOptions;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public enum TLSCert {

  NONE() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return null;
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return null;
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return null;
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      return null;
    }
  },

  // Self signed
  JKS() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new JksOptions().setPath("tls/server-keystore.jks").setPassword("wibble");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return new JksOptions().setPath("tls/server-truststore.jks").setPassword("wibble");
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new JksOptions().setPath("tls/client-truststore.jks").setPassword("wibble");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      return new JksOptions().setPath("tls/client-keystore.jks").setPassword("wibble");
    }
  },

  // Self signed
  PKCS12() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new PfxOptions().setPath("tls/server-keystore.p12").setPassword("wibble");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return new PfxOptions().setPath("tls/server-truststore.p12").setPassword("wibble");
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new PfxOptions().setPath("tls/client-truststore.p12").setPassword("wibble");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      return new PfxOptions().setPath("tls/client-keystore.p12").setPassword("wibble");
    }
  },

  // Self signed
  PEM() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert.pem");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/client-cert.pem");
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/server-cert.pem");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      return new PemKeyCertOptions().setKeyPath("tls/client-key.pem").setCertPath("tls/client-cert.pem");
    }
  },

  // Signed by root CA
  JKS_ROOT_CA() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new JksOptions().setPath("tls/server-keystore-root-ca.jks").setPassword("wibble");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      throw new UnsupportedOperationException();
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new JksOptions().setPath("tls/client-truststore-root-ca.jks").setPassword("wibble");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      throw new UnsupportedOperationException();
    }
  },

  // Signed by root CA
  PKCS12_ROOT_CA() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new PfxOptions().setPath("tls/server-keystore-root-ca.p12").setPassword("wibble");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      throw new UnsupportedOperationException();
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new PfxOptions().setPath("tls/client-truststore-root-ca.p12").setPassword("wibble");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      throw new UnsupportedOperationException();
    }
  },

  // Signed by root CA
  PEM_ROOT_CA() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-root-ca.pem");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      return new PemKeyCertOptions().setKeyPath("tls/client-key.pem").setCertPath("tls/client-cert-root-ca.pem");
    }
  },

  // Signed by an intermediate CA
  PEM_CA() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-int-ca.pem");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/int-ca/ca-cert.pem");
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/int-ca/ca-cert.pem");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      throw new UnsupportedMessageTypeException();
    }
  },

  // Signed by an intermediate CA using a chain
  PEM_CA_CHAIN() {
    @Override
    public KeyCertOptions getServerKeyCertOptions() {
      return new PemKeyCertOptions().setKeyPath("tls/server-key.pem").setCertPath("tls/server-cert-ca-chain.pem");
    }
    @Override
    public TrustOptions getServerTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
    }
    @Override
    public TrustOptions getClientTrustOptions() {
      return new PemTrustOptions().addCertPath("tls/root-ca/ca-cert.pem");
    }
    @Override
    public KeyCertOptions getClientKeyCertOptions() {
      throw new UnsupportedMessageTypeException();
    }
  },

  // Man-in-middle attack : the server cert CN does not match the resolved URI host
  MIM() {
      @Override
      public KeyCertOptions getServerKeyCertOptions() {
          return new JksOptions().setPath("tls/mim-server-keystore.jks").setPassword("wibble");
      }
      @Override
      public TrustOptions getServerTrustOptions() {
          return null;
      }
      @Override
      public TrustOptions getClientTrustOptions() {
          return null;
      }
      @Override
      public KeyCertOptions getClientKeyCertOptions() {
          return null;
      }
  };


  public abstract KeyCertOptions getServerKeyCertOptions();
  public abstract KeyCertOptions getClientKeyCertOptions();
  public abstract TrustOptions getServerTrustOptions();
  public abstract TrustOptions getClientTrustOptions();

}
