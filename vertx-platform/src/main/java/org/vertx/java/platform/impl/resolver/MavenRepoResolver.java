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
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.platform.impl.ModuleIdentifier;
import org.vertx.java.platform.impl.resolver.requesters.GetRequest;
import org.vertx.java.platform.impl.resolver.requesters.HeadRequest;

import java.io.IOException;
import java.util.Date;

public class MavenRepoResolver extends HttpRepoResolver {

  public MavenRepoResolver(Vertx vertx, String repoID) {
    super(vertx, repoID);
  }

  public boolean isOldStyle() {
    return false;
  }

  @Override
  public void findResource(ModuleIdentifier moduleIdentifier) {
    MavenRepoResource mavenRepoResource = new MavenRepoResource.Builder().
        moduleIdentifier(moduleIdentifier).
        contentRoot(contentRoot).
        build();

    if (moduleIdentifier.isSnapshot()) {
      // Read metadata for snapshot resolution
      GetRequest getRequest = createGetRequest();
      getRequest.addHandler(200, createMetadataOKHandler(mavenRepoResource));
      getRequest.addHandler(404, createMetadataNotFoundHandler(mavenRepoResource));
      getRequest.setUnknownStatusHandler(createFailOnUnknownStatusFindHandler());

      // First we make a request to maven-metadata.xml
      String mavenMetadataPath = PathUtils.getPath(true, mavenRepoResource.getResourceDir(true), "maven-metadata.xml");
      getRequest.send(mavenMetadataPath);
    } else {
      performFindHeadRequest(mavenRepoResource, true);
    }
  }

  private Handler<HttpClientResponse> createMetadataOKHandler(final MavenRepoResource mavenRepoResource) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        resp.bodyHandler(new Handler<Buffer>() {
          @Override
          public void handle(Buffer metaData) {
            handleMetadataOK(metaData);
          }

          private void handleMetadataOK(Buffer metaData) {
            final String data = metaData.toString();
            try {
              mavenRepoResource.updateWithMetadata(data);
              performFindHeadRequest(mavenRepoResource, true);
            } catch (IOException e) {
              log.warn("Error parsing the maven-metadata.xml", e);
            }
          }
        });
      }
    };
  }

  private Handler<HttpClientResponse> createMetadataNotFoundHandler(final MavenRepoResource mavenRepoResource) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        // No maven-meta-data.xml - try the direct module name
        //TODO obtain timestamp?
        performFindHeadRequest(mavenRepoResource, true);
      }
    };
  }

  private Handler<HttpClientResponse> createResourceFoundHandler(final ModuleIdentifier modId, final String uri, final Date timestamp) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        findWaiter.end(new ResolverResult(MavenRepoResolver.this, modId, true, uri, false, timestamp));
      }
    };
  }

  private Handler<HttpClientResponse> createResourceNotFoundWithSuffixHandler(final MavenRepoResource mavenRepoResource) {
    return new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        // Not found with -mod suffix - try the old naming (we keep this for backward compatibility)
        performFindHeadRequest(mavenRepoResource, false);
      }
    };
  }

  private void performFindHeadRequest(MavenRepoResource mavenRepoResource, boolean modSuffix) {
    String uri = mavenRepoResource.generateURI(true, modSuffix);
    HeadRequest headRequest = createHeadRequest();
    headRequest.addHandler(200, createResourceFoundHandler(mavenRepoResource.getModuleIdentifier(), uri,
        mavenRepoResource.getTimestamp()));
    if (modSuffix) {
      // if we already tried without the suffix then just let it fail, otherwise add a 404 handler so we try again
      // without the suffix if the request with it fails
      headRequest.addHandler(404, createResourceNotFoundWithSuffixHandler(mavenRepoResource));
    }
    headRequest.setUnknownStatusHandler(createFailOnUnknownStatusFindHandler());
    headRequest.send(uri);
  }
}
