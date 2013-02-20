package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.Vertx;

/*
 * Copyright 2008-2011 Red Hat, Inc.
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
public class MavenRepoResolver extends HttpRepoResolver {

  public MavenRepoResolver(Vertx vertx, String repoID) {
    super(vertx, repoID);
  }

  @Override
  protected String getRepoURI(String moduleName) {

    String[] parts = moduleName.split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException(moduleName + " must be of the form <group_id>:<artifact_id>:<version>");
    }

    String groupID = parts[0];
    String artifactID = parts[1];
    String version = parts[2];

    StringBuilder uri = new StringBuilder(contentRoot);
    uri.append('/');
    String[] groupParts = groupID.split("\\.");
    for (String groupPart: groupParts) {
      uri.append(groupPart).append('/');
    }
    uri.append(artifactID).append('/').append(version).append('/').
        append(artifactID).append('-').append(version).append(".zip");
    return uri.toString();
  }

}
