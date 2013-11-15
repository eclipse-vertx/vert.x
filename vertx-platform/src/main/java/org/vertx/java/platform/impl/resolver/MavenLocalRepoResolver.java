/*
 * Copyright (c) 2011-2013 The original author or authors
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

package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

public class MavenLocalRepoResolver implements RepoResolver {

  private static final Logger log = LoggerFactory.getLogger(MavenLocalRepoResolver.class);

  private final String repoID;
  private static final String homeDir = System.getProperty("user.home");

  public MavenLocalRepoResolver(String repoID) {
    this.repoID = expandHome(repoID);
  }

  private static String expandHome(String repo) {
    return repo.replace("~", homeDir);
  }

  private boolean getModuleForMetaData(String filename,
                                       ModuleIdentifier id,
                                       File metaDataFile,
                                       String uriRoot) {
    try (Scanner scanner = new Scanner(metaDataFile).useDelimiter("\\A")) {
      String data = scanner.next();
      // First try with the -mod suffix
      String fileName = MavenResolution.getResourceName(data, repoID, id, uriRoot, true);
      File file = new File(fileName);
      if (!file.exists()) {
        // And then without (for backward compatibility)
        fileName = MavenResolution.getResourceName(data, repoID, id, uriRoot, false);
        file = new File(fileName);
      }
      if (file.exists()) {
        try {
          Files.copy(file.toPath(), Paths.get(filename));
          return true;
        } catch (IOException e) {
          log.error("Failed to copy file", e);
          return false;
        }
      } else {
        return false;
      }
    } catch (IOException e) {
      log.error("Failed to read file", e);
      return false;
    }
  }

  @Override
  public boolean getModule(String filename, ModuleIdentifier moduleIdentifier) {
    String uriRoot = MavenResolution.getMavenURI(moduleIdentifier);
    File localMetaDataFile =
        new File(repoID + '/' + uriRoot + "maven-metadata-local.xml");
    File remoteMetaDataFile =
        new File(repoID + '/' + uriRoot + "maven-metadata-remote.xml");
    boolean lExists = localMetaDataFile.exists();
    boolean rExists = remoteMetaDataFile.exists();
    if ((lExists && !rExists) || (lExists && rExists && localMetaDataFile.lastModified() >= remoteMetaDataFile.lastModified())) {
      return getModuleForMetaData(filename, moduleIdentifier, localMetaDataFile, uriRoot);
    } else if (rExists) {
      return getModuleForMetaData(filename, moduleIdentifier, remoteMetaDataFile, uriRoot);
    } else {
      // First try with -mod suffix
      String prefix = repoID + '/' + uriRoot + moduleIdentifier.getName() + '-' + moduleIdentifier.getVersion();
      File nonSnapshotFile = new File(prefix + "-mod.zip");
      if (!nonSnapshotFile.exists()) {
        // And then with no prefix for backward compatibility
        nonSnapshotFile = new File(prefix + ".zip");
      }
      if (nonSnapshotFile.exists()) {
        try {
          Files.copy(nonSnapshotFile.toPath(), Paths.get(filename));
          return true;
        } catch (IOException e) {
          log.error("Failed to copy file", e);
          return false;
        }
      } else {
        return false;
      }
    }
  }

  public boolean isOldStyle() {
    return false;
  }
}
