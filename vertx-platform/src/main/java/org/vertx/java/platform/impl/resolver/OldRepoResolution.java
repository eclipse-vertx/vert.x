package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.*;
import org.vertx.java.core.http.HttpClientResponse;

import java.util.UUID;

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
 */
public class OldRepoResolution extends HttpResolution {

  private final String contentRoot;

  public OldRepoResolution(Vertx vertx, String repoHost, int repoPort, String moduleName, String filename,
                           String contentRoot) {
    super(vertx, repoHost, repoPort, moduleName, filename);
    this.contentRoot = contentRoot;
  }

  @Override
  protected void getModule() {
    createClient(repoHost, repoPort);
    addHandler(404, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        //NOOP
        end(false);
      }
    });
    addHandler(200, new Handler<HttpClientResponse>() {
      @Override
      public void handle(HttpClientResponse resp) {
        downloadToFile(filename, resp);
      }
    });
    String uri = contentRoot + "/" + moduleName + "/mod.zip";
    makeRequest(repoHost, repoPort, uri);
  }
}
