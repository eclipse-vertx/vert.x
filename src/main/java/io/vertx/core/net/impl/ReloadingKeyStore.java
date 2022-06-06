/*
* Copyright (c) 2011-2022 Contributors to the Eclipse Foundation
*
* This program and the accompanying materials are made available under the
* terms of the Eclipse Public License 2.0 which is available at
* http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
* which is available at https://www.apache.org/licenses/LICENSE-2.0.
*
* SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
*/

package io.vertx.core.net.impl;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreSpi;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.cert.CertificateException;
import java.util.List;
import java.util.Objects;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;

/**
 * KeyStore that can reload itself when the backing files are modified.
 */
public class ReloadingKeyStore extends KeyStore {

  protected ReloadingKeyStore(KeyStoreSpi keyStoreSpi, Provider provider, String type)
      throws NoSuchAlgorithmException, CertificateException, IOException {
    super(keyStoreSpi, provider, type);

    // Calling load(), even with null arguments, will initialize the KeyStore to expected state.
    load(null, null);
  }

  /**
   * Builder implementation for reloading keystores.
   */
  public static class Builder extends KeyStore.Builder {

    private final KeyStore keyStore;
    private final ProtectionParameter protection;

    private final String alias;
    private final ProtectionParameter aliasProtection;

    private Builder(KeyStore keyStore, String password, String alias, String keyAliasPassword) {
      this.keyStore = keyStore;
      this.protection = password != null ? new PasswordProtection(password.toCharArray()) : null;
      this.alias = alias;
      this.aliasProtection = keyAliasPassword != null ? new PasswordProtection(keyAliasPassword.toCharArray()) : null;
    }

    @Override
    public KeyStore getKeyStore() {
      return keyStore;
    }

    @Override
    public ProtectionParameter getProtectionParameter(String newSunAlias) {
      Objects.requireNonNull(newSunAlias);

      // Parse plain alias from NewSunS509 KeyManager prefixed alias.
      // https://github.com/openjdk/jdk/blob/6e55a72f25f7273e3a8a19e0b9a97669b84808e9/src/java.base/share/classes/sun/security/ssl/X509KeyManagerImpl.java#L237-L265
      int firstDot = newSunAlias.indexOf('.');
      int secondDot = newSunAlias.indexOf('.', firstDot + 1);
      if ((firstDot == -1) || (secondDot == firstDot)) {
        // Invalid alias.
        return protection;
      }
      String requestedAlias = newSunAlias.substring(secondDot + 1);
      if (requestedAlias.equals(alias) && aliasProtection != null) {
        return aliasProtection;
      }
      return protection;
    }

    public static Builder fromKeyStoreFile(VertxInternal vertx, String type, String provider, String path, String password,
        String alias, String aliasPassword) throws Exception {
      return new Builder(new ReloadingKeyStore(
        new KeyStoreFileSpi(vertx, type, provider, path, password, alias), null, "ReloadingKeyStore"),
        password, alias, aliasPassword);
    }

    public static Builder fromPem(VertxInternal vertx, List<String> certPaths, List<String> keyPaths,
        List<Buffer> certValues, List<Buffer> keyValues) throws Exception {
      return new Builder(new ReloadingKeyStore(
        new PemFileKeyStoreSpi(vertx, certPaths, keyPaths, certValues, keyValues), null, "ReloadingKeyStore"),
          KeyStoreHelper.DUMMY_PASSWORD, null, null);
    }

  }

}
