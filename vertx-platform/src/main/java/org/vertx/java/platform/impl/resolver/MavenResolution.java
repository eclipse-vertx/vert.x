package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClientResponse;

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
 * Maven module names must be of the form:
 *
 * group_id:artifact_id:version
 *
 * e.g.
 *
 * org.mycompany.foo:foo_module:1.0.2-SNAPSHOT
 */
public class MavenResolution extends HttpResolution {

  protected String contentRoot;
  protected MavenIdentifier identifier;

  public MavenResolution(Vertx vertx, String repoHost, int repoPort, String moduleName, String filename,
                         String contentRoot) {
    super(vertx, repoHost, repoPort, moduleName, filename);
    this.contentRoot = contentRoot;
    identifier = new MavenIdentifier(moduleName);
  }

  protected void getModule() {
    createClient(repoHost, repoPort);
    addHandler(404, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        //NOOP
        end(false);
      }
    });
    if (identifier.version.endsWith("-SNAPSHOT")) {
      addHandler(200, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse resp) {
          resp.bodyHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer metaData) {
              // Extract the timestamp - easier this way than parsing the xml
              String data = metaData.toString();
              String actualURI = getResourceName(data, contentRoot, identifier);
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
      makeRequest(repoHost, repoPort, contentRoot + "/" + identifier.uriRoot + "maven-metadata.xml");
    } else {
      addHandler(200, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse resp) {
          downloadToFile(filename, resp);
        }
      });
      makeRequest(repoHost, repoPort, getNonVersionedResourceName(contentRoot, identifier));
    }
  }

  static String getResourceName(String data, String contentRoot, MavenIdentifier identifier) {
    int pos = data.indexOf("<snapshot>");
    String actualURI = null;
    if (pos != -1) {
      int pos2 = data.indexOf("<timestamp>", pos);
      if (pos2 != -1) {
        String timestamp = data.substring(pos2 + 11, pos2 + 26);
        int pos3 = data.indexOf("<buildNumber>", pos);
        int pos4 = data.indexOf("<", pos3 + 12);
        String buildNumber = data.substring(pos3 + 13, pos4);
        // Timestamped SNAPSHOT
        actualURI = contentRoot + "/" + identifier.uriRoot + identifier.artifactID + "-" +
            identifier.version.substring(0, identifier.version.length() - 9) + "-" +
            timestamp + "-" + buildNumber + ".zip";
      }
    }
    if (actualURI == null) {
      // Non timestamped SNAPSHOT
      actualURI = getNonVersionedResourceName(contentRoot, identifier);
    }
    return actualURI;
  }

  private static String getNonVersionedResourceName(String contentRoot, MavenIdentifier identifier) {
    return contentRoot + "/" + identifier.uriRoot + identifier.artifactID + "-" + identifier.version + ".zip";
  }

}
