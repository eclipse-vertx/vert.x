package org.vertx.java.platform.impl.resolver;/*
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

import org.vertx.java.core.Vertx;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.net.URI;

public abstract class HttpRepoResolver implements RepoResolver {

  private static final Logger log = LoggerFactory.getLogger(HttpRepoResolver.class);

  protected final Vertx vertx;
  protected final String repoHost;
  protected final int repoPort;
  protected final String contentRoot;

  public HttpRepoResolver(Vertx vertx, String repoID) {
    this.vertx = vertx;
    try {
      URI uri = new URI(repoID);
      repoHost = uri.getHost();
      int port = uri.getPort();
      if (port == -1) {
        port = 80;
      }
      repoPort = port;
      contentRoot = uri.getPath();
    } catch (Exception e) {
      throw new IllegalArgumentException(repoID + " is not a valid repository identifier");
    }
  }

  public abstract boolean getModule(String filename, String moduleName);

}
