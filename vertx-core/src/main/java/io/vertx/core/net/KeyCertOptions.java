/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.vertx.core.net;

import io.vertx.core.buffer.Buffer;

/**
 * Key store options configuring a private key and its certificate based on
 * <i>Privacy-enhanced Electronic Email</i> (PEM) files.<p>
 *
 * The key file must contain a <b>non encrypted</b> private key in <b>PKCS8</b> format wrapped in a PEM
 * block, for example:<p>
 *
 * <pre>
 * -----BEGIN PRIVATE KEY-----
 * MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDV6zPk5WqLwS0a
 * ...
 * K5xBhtm1AhdnZjx5KfW3BecE
 * -----END PRIVATE KEY-----
 * </pre><p>
 *
 * The certificate file must contain an X.509 certificate wrapped in a PEM block, for example:<p>
 *
 * <pre>
 * -----BEGIN CERTIFICATE-----
 * MIIDezCCAmOgAwIBAgIEZOI/3TANBgkqhkiG9w0BAQsFADBuMRAwDgYDVQQGEwdV
 * ...
 * +tmLSvYS39O2nqIzzAUfztkYnUlZmB0l/mKkVqbGJA==
 * -----END CERTIFICATE-----
 * </pre>
 *
 * The key and certificate can either be loaded by Vert.x from the filesystem:<p>
 * <pre>
 * HttpServerOptions options = new HttpServerOptions();
 * options.setKeyStore(new KeyCertOptions().setKeyPath("/mykey.pem").setCertPath("/mycert.pem"));
 * </pre>
 *
 * Or directly provided as a buffer:<p>
 *
 * <pre>
 * Buffer key = vertx.fileSystem().readFileSync("/mykey.pem");
 * Buffer cert = vertx.fileSystem().readFileSync("/mycert.pem");
 * options.setKeyStore(new KeyCertOptions().setKeyValue(key).setCertValue(cert));
 * </pre>
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class KeyCertOptions implements KeyStoreOptions {

  private String keyPath;
  private Buffer keyValue;
  private String certPath;
  private Buffer certValue;

  public KeyCertOptions() {
    super();
  }

  public KeyCertOptions(KeyCertOptions other) {
    super();
    this.keyPath = other.keyPath;
    this.certPath = other.certPath;
  }

  public String getKeyPath() {
    return keyPath;
  }

  public KeyCertOptions setKeyPath(String keyPath) {
    this.keyPath = keyPath;
    return this;
  }

  public String getCertPath() {
    return certPath;
  }

  public Buffer getKeyValue() {
    return keyValue;
  }

  public KeyCertOptions setKeyValue(Buffer keyValue) {
    this.keyValue = keyValue;
    return this;
  }

  public KeyCertOptions setCertPath(String certPath) {
    this.certPath = certPath;
    return this;
  }

  public Buffer getCertValue() {
    return certValue;
  }

  public KeyCertOptions setCertValue(Buffer certValue) {
    this.certValue = certValue;
    return this;
  }

  @Override
  public KeyCertOptions clone() {
    return new KeyCertOptions(this);
  }
}
