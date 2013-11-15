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

import org.vertx.java.core.Vertx;
import org.vertx.java.platform.impl.ModuleIdentifier;

public class BintrayRepoResolver extends HttpRepoResolver {

  public BintrayRepoResolver(Vertx vertx, String repoID) {
    super(vertx, repoID);
  }

  @Override
  public boolean getModule(String filename, ModuleIdentifier modID) {
    HttpResolution res = new BintrayResolution(vertx, repoScheme, repoHost, repoPort, modID, filename, contentRoot);
    res.getModule();
    return res.waitResult();
  }

  public boolean isOldStyle() {
    return false;
  }

}
