package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.platform.impl.ModuleIdentifier;

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
 * This resolver works with any HTTP server that can serve modules from GETs to Maven style urls
 *
 */
public class MavenResolution extends HttpResolution {

  protected String contentRoot;
  protected ModuleIdentifier moduleIdentifier;
  protected String uriRoot;

  public MavenResolution(Vertx vertx, String repoHost, int repoPort, ModuleIdentifier moduleIdentifier, String filename,
                         String contentRoot) {
    super(vertx, repoHost, repoPort, moduleIdentifier, filename);
    this.contentRoot = contentRoot;
    this.moduleIdentifier = moduleIdentifier;
    uriRoot = getMavenURI(moduleIdentifier);
  }

  protected void getModule() {
    createClient(repoHost, repoPort);
    if (moduleIdentifier.getVersion().endsWith("-SNAPSHOT")) {
      addHandler(200, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse resp) {
          resp.bodyHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer metaData) {
              // Extract the timestamp - easier this way than parsing the xml
              String data = metaData.toString();
              String actualURI = getResourceName(data, contentRoot, moduleIdentifier, uriRoot);
              addHandler(200, new Handler<HttpClientResponse>() {
                @Override
                public void handle(HttpClientResponse resp) {
                  downloadToFile(filename, resp);
                }
              });
              makeRequest(repoHost, repoPort, actualURI);
            }
          });
        }
      });
      // First we make a request to maven-metadata.xml
      makeRequest(repoHost, repoPort, contentRoot + '/' + uriRoot + "maven-metadata.xml");
    } else {
      addHandler(200, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse resp) {
          downloadToFile(filename, resp);
        }
      });
      makeRequest(repoHost, repoPort, getNonVersionedResourceName(contentRoot, moduleIdentifier, uriRoot));
    }
  }

  static String getResourceName(String data, String contentRoot, ModuleIdentifier identifier, String uriRoot) {
    int pos = data.indexOf("<snapshot>");
    String actualURI = null;
    if (pos != -1) {
      int pos2 = data.indexOf("<timestamp>", pos);
      if (pos2 != -1) {
        String timestamp = data.substring(pos2 + 11, pos2 + 26);
        int pos3 = data.indexOf("<buildNumber>", pos);
        int pos4 = data.indexOf('<', pos3 + 12);
        String buildNumber = data.substring(pos3 + 13, pos4);
        // Timestamped SNAPSHOT
        actualURI = contentRoot + '/' + uriRoot + identifier.getName() + '-' +
            identifier.getVersion().substring(0, identifier.getVersion().length() - 9) + '-' +
            timestamp + '-' + buildNumber + "-mod.zip";
      }
    }
    if (actualURI == null) {
      // Non timestamped SNAPSHOT
      actualURI = getNonVersionedResourceName(contentRoot, identifier, uriRoot);
    }
    return actualURI;
  }

  static String getMavenURI(ModuleIdentifier moduleIdentifier) {
    StringBuilder uri = new StringBuilder('/');
    String[] groupParts = moduleIdentifier.getOwner().split("\\.");
    for (String groupPart: groupParts) {
      uri.append(groupPart).append('/');
    }
    uri.append(moduleIdentifier.getName()).append('/').append(moduleIdentifier.getVersion()).append('/');
    return uri.toString();
  }

  private static String getNonVersionedResourceName(String contentRoot, ModuleIdentifier identifier, String uriRoot) {
    return contentRoot + '/' + uriRoot + identifier.getName() + '-' + identifier.getVersion() + "-mod.zip";
  }

}
