package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;

import java.net.URI;
import java.net.URISyntaxException;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 *
 * Given a module this resolver will look for the module at:
 *
 * content_root/<owner>/vertx-mods/<module-name>/<module-name>-<version>.zip
 *
 * That's the recommended path for users to put modules in bintray, Vert.x can still find modules in bintray in
 * other paths if you use the GenericHttpRepoResolver
 */
public class BintrayResolution extends HttpResolution {

  private static final Logger log = LoggerFactory.getLogger(BintrayResolution.class);

  private final String uri;

  public BintrayResolution(Vertx vertx, String repoHost, int repoPort, ModuleIdentifier moduleID, final String filename, String contentRoot) {
    super(vertx, repoHost, repoPort, moduleID, filename);
    addHandler(200, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        downloadToFile(filename, resp);
      }
    });
    addHandler(302, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        handle302(resp);
      }
    });

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
    createClient(repoHost, repoPort);
    makeRequest(repoHost, repoPort, uri);
  }

  protected void handle302(HttpClientResponse resp) {
    // follow redirects
    String location = resp.headers().get("location");
    if (location == null) {
      log.error("HTTP redirect with no location header");
    } else {
      URI redirectURI;
      try {
        redirectURI = new URI(location);
        client.close();
        client = null;
        int redirectPort = redirectURI.getPort();
        if (redirectPort == -1) {
          redirectPort = 80;
        }
        createClient(redirectURI.getHost(), redirectPort);
        makeRequest(redirectURI.getHost(), redirectPort, redirectURI.getPath());
      } catch (URISyntaxException e) {
        log.error("Invalid redirect URI: " + location);
      }
    }
  }
}
