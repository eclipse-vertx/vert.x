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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import io.vertx.core.VertxException;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;

public class PemFileKeyStoreSpi extends DelegatingKeyStoreSpi {

  private static final Logger log = LoggerFactory.getLogger(PemFileKeyStoreSpi.class);

  private final VertxInternal vertx;
  private final List<FileCredential> fileCredentials = new ArrayList<>(); // Certificates and keys given as paths.
  private final List<Buffer> certValues;
  private final List<Buffer> keyValues;

  public PemFileKeyStoreSpi(VertxInternal vertx, List<String> certPaths, List<String> keyPaths,
      List<Buffer> certValues, List<Buffer> keyValues) throws Exception {

    if ((keyPaths.size() < certPaths.size()) || (keyValues.size() < certValues.size())) {
      throw new VertxException("Missing private key");
    } else if ((keyPaths.size() > certPaths.size()) || (keyValues.size() > certValues.size())) {
      throw new VertxException("Missing X.509 certificate");
    } else if (keyPaths.isEmpty() && keyValues.isEmpty()) {
      throw new VertxException("No credentials configured");
    }

    this.vertx = vertx;
    this.certValues = certValues;
    this.keyValues = keyValues;

    // Load credentials that were passed as file paths.
    Iterator<String> cpi = certPaths.iterator();
    Iterator<String> kpi = keyPaths.iterator();
    while (cpi.hasNext() && kpi.hasNext()) {
      fileCredentials.add(new FileCredential(cpi.next(), kpi.next()));
    }

    setKeyStoreDelegate(createKeyStore());
  }

  /**
   * Reload certificate and key PEM files if they were modified on disk since they were last loaded.
   */
  void refresh() throws Exception {
    boolean wasReloaded = false;
    int i = 0;
    for (FileCredential fc : fileCredentials) {
      try {
        if (fc.needsReload()) {
          fileCredentials.set(i, new FileCredential(fc.certPath, fc.keyPath));
          wasReloaded = true;
        }
      } catch (Exception e) {
        log.error("Failed to load: " + e);
      }
      i++;
    }

    // Re-generate KeyStore.
    if (wasReloaded) {
      setKeyStoreDelegate(createKeyStore());
    }
  }

  /**
   * Create KeyStore that contains the certificates and keys that were passed by paths and by values.
   */
  KeyStore createKeyStore() throws Exception {
    List<Buffer> certs = new ArrayList<>(certValues);
    List<Buffer> keys = new ArrayList<>(keyValues);

    fileCredentials.stream().forEach(fc -> {
      certs.add(fc.certValue);
      keys.add(fc.keyValue);
    });

    return KeyStoreHelper.loadKeyCert(keys, certs);
  }

  /**
   * Holds the content of certificate and key files and their modification timestamps.
   */
  class FileCredential {
    private final String certPath;
    private final String keyPath;
    private final FileTime certLastModified;
    private final FileTime keyLastModified;
    Buffer certValue;
    Buffer keyValue;

    FileCredential(String certPath, String keyPath) throws Exception {
      this.certPath = certPath;
      this.keyPath = keyPath;

      certValue = vertx.fileSystem().readFileBlocking(certPath);
      keyValue = vertx.fileSystem().readFileBlocking(keyPath);

      this.certLastModified = Files.getLastModifiedTime(Paths.get(certPath));
      this.keyLastModified = Files.getLastModifiedTime(Paths.get(keyPath));
    }

    boolean needsReload() throws IOException {
      return (certLastModified.compareTo(Files.getLastModifiedTime(Paths.get(certPath))) < 0) ||
          (keyLastModified.compareTo(Files.getLastModifiedTime(Paths.get(keyPath))) < 0);
    }
  }

}
