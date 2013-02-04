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

/*
This resolver works with the old github-based Vert.x module repository used in Vert.x 1.x
 */
public class Vertx1xResolver extends HttpRepoResolver {

  public Vertx1xResolver(Vertx vertx, String proxyHost, int proxyPort, String repoID) {
    super(vertx, proxyHost, proxyPort, repoID);
  }

  @Override
  protected String getRepoURI(String moduleName) {
    return contentRoot + moduleName + "/mod.zip";
  }

}
