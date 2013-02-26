package org.vertx.java.platform.impl.resolver;

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
public class MavenIdentifier {

  public MavenIdentifier(String moduleName) {
    String[] parts = moduleName.split(":");
    if (parts.length != 3) {
      throw new IllegalArgumentException(moduleName + " must be of the form <group_id>:<artifact_id>:<version>");
    }
    groupID = parts[0];
    artifactID = parts[1];
    version = parts[2];
    StringBuilder uri = new StringBuilder('/');
    String[] groupParts = groupID.split("\\.");
    for (String groupPart: groupParts) {
      uri.append(groupPart).append('/');
    }
    uri.append(artifactID).append('/').append(version).append('/');
    uriRoot = uri.toString();
  }

  public final String groupID;
  public final String artifactID;
  public final String version;
  public final String uriRoot;
}
