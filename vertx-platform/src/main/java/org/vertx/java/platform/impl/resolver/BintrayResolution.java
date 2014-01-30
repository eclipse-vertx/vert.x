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

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;

public class BintrayResolution extends HttpResolution {

  private static final Logger log = LoggerFactory.getLogger(BintrayResolution.class);

  private final String uri;

  public BintrayResolution(Vertx vertx, String repoScheme, String repoHost, int repoPort, ModuleIdentifier moduleID, final String filename, String contentRoot) {
    super(vertx, repoScheme, null, null, repoHost, repoPort, moduleID, filename);
    addHandler(200, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        downloadToFile(filename, resp);
      }
    });
    addRedirectHandlers();

    String user = moduleID.getOwner();
    String repo = "vertx-mods";
    String modName = moduleID.getName();
    String version = moduleID.getVersion();

    StringBuilder sb = new StringBuilder(contentRoot);
    sb.append('/');
    sb.append(user).append('/').append(repo).append('/').
        append(modName).append('/').append(modName).append('-').append(version).append(".zip");
    uri = sb.toString();
  }

  @Override
  protected void getModule() {
    createClient(repoScheme, repoHost, repoPort);
    makeRequest(repoScheme, repoHost, repoPort, uri);
  }


}
