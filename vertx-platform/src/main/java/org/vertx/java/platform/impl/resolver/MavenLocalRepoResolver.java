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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;

public class MavenLocalRepoResolver implements RepoResolver {

  private static final Logger log = LoggerFactory.getLogger(MavenLocalRepoResolver.class);
  private static final String homeDir = System.getProperty("user.home");

  private final String repoRootPath;

  public MavenLocalRepoResolver(String repoRootPath) {
    this.repoRootPath = expandHome(repoRootPath);
  }

  private static String expandHome(String repo) {
    return repo.replace("~", homeDir);
  }

  private static File getFile(MavenRepoResource mavenRepoResource) {
    // First try with the -mod suffix
    String originPath = mavenRepoResource.generateURI(false, true);
    File file = new File(originPath);
    if (!file.exists()) {
      // And then without (for backward compatibility)
      originPath = mavenRepoResource.generateURI(false, false);
      file = new File(originPath);
    }
    return file;
  }

  @Override
  public ResolverResult findModule(ModuleIdentifier moduleIdentifier) {
    MavenRepoResource mavenRepoResource = new MavenRepoResource.Builder().
        moduleIdentifier(moduleIdentifier).
        contentRoot(repoRootPath).
        build();

    String resourcePath = mavenRepoResource.getResourceDir(false);
    File localMetaDataFile = new File(PathUtils.getPath(false, resourcePath, "maven-metadata-local.xml"));
    File remoteMetaDataFile = new File(PathUtils.getPath(false, resourcePath, "maven-metadata-remote.xml"));
    boolean lExists = localMetaDataFile.exists();
    boolean rExists = remoteMetaDataFile.exists();
    if (lExists && (!rExists || localMetaDataFile.lastModified() >= remoteMetaDataFile.lastModified())) {
      return getModuleForMetaData(moduleIdentifier, localMetaDataFile);
    } else if (rExists) {
      return getModuleForMetaData(moduleIdentifier, remoteMetaDataFile);
    } else {
      File file = getFile(mavenRepoResource);
      if (file.exists()) {
        // Using standard base time because the old repo resolution has no timestamp.
        // This implies that if this artifact is a SNAPSHOT, it will only be used if there are no other SNAPSHOT
        // versions on other non-old-repos.
        return new ResolverResult(this, moduleIdentifier, true, file.getAbsolutePath(), true, new Date(0));
      } else {
        return ResolverResult.FAILED;
      }
    }
  }

  private ResolverResult getModuleForMetaData(ModuleIdentifier modId, File metaDataFile) {
    MavenRepoResource mavenRepoResource = new MavenRepoResource.Builder().
        moduleIdentifier(modId).
        contentRoot(repoRootPath).
        build();

    if (modId.isSnapshot()) {
      if (!mavenRepoResource.updateWithMetadata(metaDataFile)) {
        return ResolverResult.FAILED;
      }
    }

    File file = getFile(mavenRepoResource);
    if (file.exists()) {
      Date timestamp = mavenRepoResource.getTimestamp() == null ? mavenRepoResource.getLastUpdated() : mavenRepoResource.getTimestamp();
      return new ResolverResult(this, modId, true, file.getAbsolutePath(), true, timestamp);
    } else {
      return ResolverResult.FAILED;
    }
  }

  @Override
  public boolean obtainModule(ResolverResult resourceData, Path targetPath) {
    try {
      Files.copy(Paths.get(resourceData.getOriginURI()), targetPath);
      return true;
    } catch (IOException e) {
      log.error("Failed to copy file", e);
    }
    return false;
  }

  @Override
  public void close() {
  }

  public boolean isOldStyle() {
    return false;
  }
}
