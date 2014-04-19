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
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.ModuleIdentifier;

public class MavenResolution extends HttpResolution {
  private static final Logger log = LoggerFactory.getLogger(MavenResolution.class);
  protected String contentRoot;
  protected ModuleIdentifier moduleIdentifier;
  protected String uriRoot;

  public MavenResolution(Vertx vertx, String repoScheme, String repoUsername, String repoPassword, String repoHost, int repoPort, ModuleIdentifier moduleIdentifier, String filename,
                         String contentRoot) {
    super(vertx, repoScheme, repoUsername, repoPassword, repoHost, repoPort, moduleIdentifier, filename);
    this.contentRoot = contentRoot;
    this.moduleIdentifier = moduleIdentifier;
    uriRoot = getMavenURI(moduleIdentifier);
  }

  private void addOKHandler() {
    addHandler(200, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        downloadToFile(filename, resp);
      }
    });
  }

  protected void getModule() {
    createClient(repoScheme, repoHost, repoPort);
    if (moduleIdentifier.getVersion().endsWith("-SNAPSHOT")) {
      addHandler(200, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse resp) {
          resp.bodyHandler(new Handler<Buffer>() {
            @Override
            public void handle(Buffer metaData) {
              // Extract the timestamp - easier this way than parsing the xml
              final String data = metaData.toString();
              String actualURI = getResourceName(data, contentRoot, moduleIdentifier, uriRoot, true);
              addOKHandler();
              addHandler(404, new Handler<HttpClientResponse>() {
                @Override
                public void handle(HttpClientResponse resp) {
                  // Not found with -mod suffix - try the old naming (we keep this for backward compatibility)
                  addOKHandler();
                  removeHandler(404);
                  String actualURI = getResourceName(data, contentRoot, moduleIdentifier, uriRoot, false);
                  makeRequest(repoScheme, repoHost, repoPort, actualURI);
                }
              });
              makeRequest(repoScheme, repoHost, repoPort, actualURI);
            }
          });
        }
      });
      addHandler(401,new Handler<HttpClientResponse>() {
          @Override
          public void handle(HttpClientResponse event) {
             log.info(event.statusCode() + " - "+event.statusMessage());
             removeHandler(401);
          }
      });
      addHandler(404, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse resp) {
          // No maven-meta-data.xml - try the direct module name
          attemptDirectDownload();
        }
      });
      addRedirectHandlers();
      // First we make a request to maven-metadata.xml
      makeRequest(repoScheme, repoHost, repoPort, contentRoot + '/' + uriRoot + "maven-metadata.xml");
    } else {
      attemptDirectDownload();
    }
  }

  protected void attemptDirectDownload() {
    addOKHandler();
    addHandler(401, new Handler<HttpClientResponse>() {
        @Override
        public void handle(HttpClientResponse event) {
            log.info(event.statusCode() + " - "+event.statusMessage());
            removeHandler(401);
        }
    });
    addHandler(404, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        // Not found with -mod suffix - try the old naming (we keep this for backward compatibility)
        addOKHandler();
        removeHandler(404);
        makeRequest(repoScheme, repoHost, repoPort, getNonVersionedResourceName(contentRoot, moduleIdentifier, uriRoot, false));
      }
    });
    addRedirectHandlers();
    makeRequest(repoScheme, repoHost, repoPort, getNonVersionedResourceName(contentRoot, moduleIdentifier, uriRoot, true));
  }

  static String getResourceName(String data, String contentRoot, ModuleIdentifier identifier, String uriRoot,
                                boolean modSuffix) {
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
            timestamp + '-' + buildNumber + (modSuffix ? "-mod" : "") + ".zip";
      }
    }
    if (actualURI == null) {
      // Non timestamped SNAPSHOT
      actualURI = getNonVersionedResourceName(contentRoot, identifier, uriRoot, modSuffix);
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

  private static String getNonVersionedResourceName(String contentRoot, ModuleIdentifier identifier, String uriRoot,
                                                    boolean modSuffix) {
    return contentRoot + '/' + uriRoot + identifier.getName() + '-' + identifier.getVersion() + (modSuffix ? "-mod" : "") + ".zip";
  }

}
