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
import org.vertx.java.platform.impl.ModuleIdentifier;
import org.vertx.java.platform.impl.resolver.requesters.GetRequest;

public class BintrayRepoResolver extends HttpRepoResolver {

  public BintrayRepoResolver(Vertx vertx, String repoID) {
    super(vertx, repoID);
  }

  public boolean isOldStyle() {
    return false;
  }

  @Override
  public void findResource(ModuleIdentifier modID) {
    if (modID.isSnapshot()) {
      // The material that should be uploaded to Bintray is publishable, working software - releases and not snapshots.
      // https://bintray.com/docs/whatisbintray/whatisbintray_forwhatbintrayshouldandshouldnotbeused.html
      findWaiter.end(ResolverResult.FAILED);
      return;
    }

    String user = modID.getOwner();
    String repo = "vertx-mods";
    String modName = modID.getName();
    String version = modID.getVersion();
    String uri = contentRoot + '/' + user + '/' + repo + '/' + modName + '/' + modName + '-' + version + ".zip";

    GetRequest getRequest = createGetRequest();
    getRequest.addHandler(200, createResourceFoundHandler(modID, uri));
    getRequest.setUnknownStatusHandler(createFailOnUnknownStatusFindHandler());
    getRequest.send(uri);
  }

  private Handler<HttpClientResponse> createResourceFoundHandler(final ModuleIdentifier modID, final String uri) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        findWaiter.end(new ResolverResult(BintrayRepoResolver.this, modID, true, uri, false, null));
      }
    };
  }
}
