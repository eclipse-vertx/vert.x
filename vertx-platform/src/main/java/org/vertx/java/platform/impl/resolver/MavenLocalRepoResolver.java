package org.vertx.java.platform.impl.resolver;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.resolver.MavenIdentifier;
import org.vertx.java.platform.impl.resolver.RepoResolver;

import java.io.File;
import java.io.IOException;
import java.lang.Override;
import java.lang.String;
import java.lang.System;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

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
public class MavenLocalRepoResolver implements RepoResolver {

  private static final Logger log = LoggerFactory.getLogger(MavenLocalRepoResolver.class);

  private final String repoID;
  private static final String homeDir = System.getProperty("user.home");

  public MavenLocalRepoResolver(String repoID) {
    this.repoID = expandHome(repoID);
  }

  private String expandHome(String repo) {
    return repo.replace("~", homeDir);
  }

  @Override
  public boolean getModule(String filename, String moduleName) {
    MavenIdentifier id = new MavenIdentifier(moduleName);
    //First look at the maven metadata
    String metaDataFileName = repoID + "/" + id.uriRoot + "maven-metadata-remote.xml";
    File metaDataFile = new File(metaDataFileName);
    if (metaDataFile.exists()) {
      try (Scanner scanner = new Scanner(metaDataFile).useDelimiter("\\A")) {
        String data = scanner.next();
        String fileName = MavenResolution.getResourceName(data, repoID, id);
        File file = new File(fileName);
        if (file.exists()) {
          try {
            Files.copy(file.toPath(), Paths.get(filename));
            return true;
          } catch (IOException e) {
            log.error("Failed to copy file", e);
            return false;
          }
        } else {
          return false;
        }
      } catch (IOException e) {
        log.error("Failed to read file", e);
        return false;
      }
    } else {
      return false;
    }
  }

  public boolean isOldStyle() {
    return false;
  }
}
