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

import java.util.Date;

public class OldRepoResolver extends HttpRepoResolver {

  public OldRepoResolver(Vertx vertx, String repoID) {
    super(vertx, repoID);
  }

  @Override
  public void findResource(ModuleIdentifier moduleIdentifier) {
    GetRequest getRequest = createGetRequest();
    String uri = contentRoot + '/' + moduleIdentifier.getOwner() + '.' + moduleIdentifier.getName() + "-v" +
        moduleIdentifier.getVersion() + "/mod.zip";
    getRequest.addHandler(200, createResourceFoundHandler(moduleIdentifier, uri));
    getRequest.addHandler(404, createResourceNotFoundHandler());
    getRequest.setUnknownStatusHandler(createFailOnUnknownStatusFindHandler());
    getRequest.send(uri);
  }

  @Override
  public boolean isOldStyle() {
    return true;
  }

  private Handler<HttpClientResponse> createResourceFoundHandler(final ModuleIdentifier moduleIdentifier,
                                                                 final String uri) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        // Using standard base time because the old repo resolution has no timestamp.
        // This implies that if this artifact is a SNAPSHOT, it will only be used if there are no other SNAPSHOT
        // versions on other non-old-repos.
        findWaiter.end(new ResolverResult(OldRepoResolver.this, moduleIdentifier, true, uri, false, new Date(0)));
      }
    };
  }

  private Handler<HttpClientResponse> createResourceNotFoundHandler() {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        //NOOP
        findWaiter.end(ResolverResult.FAILED);
      }
    };
  }
}
