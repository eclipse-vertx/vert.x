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

import org.vertx.java.platform.impl.ModuleIdentifier;

import java.nio.file.Path;
import java.util.Date;

public class ResolverResult {
  public static final ResolverResult FAILED = new ResolverResult(null, null, false, null, true, null);

  private ModuleIdentifier moduleIdentifier;
  private RepoResolver repoResolver;
  private boolean wasFound;
  private String originURI;
  private boolean isLocal;
  private Date timestamp;

  public ResolverResult(RepoResolver repoResolver, ModuleIdentifier moduleIdentifier, boolean wasFound, String originURI, boolean isLocal,
                        Date timestamp) {
    this.moduleIdentifier = moduleIdentifier;
    this.repoResolver = repoResolver;
    this.wasFound = wasFound;
    this.originURI = originURI;
    this.isLocal = isLocal;
    this.timestamp = timestamp;
  }

  public ModuleIdentifier getModuleIdentifier() {
    return moduleIdentifier;
  }

  public boolean wasFound() {
    return wasFound;
  }

  public String getOriginURI() {
    return originURI;
  }

  public boolean isLocal() {
    return isLocal;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public boolean isOldStyleRepo() {
    return repoResolver.isOldStyle();
  }

  public boolean obtainModule(Path targetPath) {
    return repoResolver.obtainModule(this, targetPath);
  }
}
