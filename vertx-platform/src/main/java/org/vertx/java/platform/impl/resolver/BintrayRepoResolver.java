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

import java.lang.Override;
import java.lang.String;

/**
 * Bintray module names must be in the form:
 *
 * user:repo:module_name:version
 *
 * Given a module name in that form, this resolver will look for the module at:
 *
 * content_root/<user>/<repo>/<module-name>/<module-name>-<version>.zip
 *
 * That's the recommended path for users to put modules in bintray, Vert.x can still find modules in bintray in
 * other paths if you use the GenericHttpRepoResolver
 */
public class BintrayRepoResolver extends HttpRepoResolver {

  public BintrayRepoResolver(Vertx vertx, String proxyHost, int proxyPort, String repoID) {
    super(vertx, proxyHost, proxyPort, repoID);
  }

  @Override
  protected String getRepoURI(String moduleName) {

    String[] parts = moduleName.split(":");
    if (parts.length != 4) {
      throw new IllegalArgumentException(moduleName + " must be of the form <user>:<repo>:<module_name>:<version>");
    }

    String user = parts[0];
    String repo = parts[1];
    String modName = parts[2];
    String version = parts[3];

    StringBuilder uri = new StringBuilder(contentRoot);
    uri.append('/');
    uri.append(user).append('/').append(repo).append('/').
        append(modName).append('/').append(modName).append('-').append(version).append(".zip");
    return uri.toString();
  }
}
