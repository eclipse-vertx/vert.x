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
 */
public class MavenRepoResolver extends HttpRepoResolver {

  public MavenRepoResolver(Vertx vertx, String proxyHost, int proxyPort, String repoID) {
    super(vertx, proxyHost, proxyPort, repoID);
  }

  @Override
  protected String getRepoURI(String moduleName) {
    ModuleIdentifier mi = new ModuleIdentifier(moduleName);

    // http://repo2.maven.org/maven2/org/jruby/jruby-complete/1.7.2/jruby-complete-1.7.2.jar
    // http://oss.sonatype.org/content/repositories/snapshots/org/vert-x/lang-rhino/1.0.0-SNAPSHOT/lang-rhino-1.0.0-SNAPSHOT.zip

    StringBuilder uri = new StringBuilder(contentRoot);
    uri.append('/');
    String[] parts = mi.group.split("\\.");

    for (String part: parts) {
      uri.append(part).append('/');
    }

    uri.append(mi.name).append('/').append(mi.version).append('/').
        append(mi.name).append('-').append(mi.version).append(".zip");

    System.out.println("uri is " + uri);

    return uri.toString();
  }
}
