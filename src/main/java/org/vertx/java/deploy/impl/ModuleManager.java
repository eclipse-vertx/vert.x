/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.java.deploy.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.DecodeException;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleManager {

  private static final Logger log = LoggerFactory.getLogger(ModuleManager.class);

  private final String modRoot;
  private final VerticleManager verticleManager;

  public ModuleManager(String modRoot, VerticleManager verticleManager) {
    this.modRoot = modRoot;
    this.verticleManager = verticleManager;
  }

  public boolean exists(String name) {
    File file = new File(modRoot, name);
    return file.exists();
  }

  // TODO cache the module info
  public void deploy(String deployName, String modName, JsonObject config, int instances, Handler<Void> doneHandler) {
    File modDir = new File(modRoot, modName);
    String conf;
    try {
      conf = new Scanner(new File(modDir, "mod.json")).useDelimiter("\\A").next();
    } catch (FileNotFoundException e) {
      throw new IllegalStateException("Module " + modName + " does not contain a mod.json file");
    }
    JsonObject json;
    try {
      json = new JsonObject(conf);
    } catch (DecodeException e) {
      throw new IllegalStateException("Module " + modName + " mod.json contains invalid json");
    }

    List<URL> urls = new ArrayList<>();
    try {
      urls.add(modDir.toURI().toURL());
      File libDir = new File(modDir, "lib");
      if (libDir.exists()) {
        File[] jars = libDir.listFiles();
        for (File jar: jars) {
          urls.add(jar.toURI().toURL());
        }
      }
    } catch (MalformedURLException e) {
      //Won't happen
      log.error("malformed url", e);
    }

    String main = json.getString("main");
    if (main == null) {
      throw new IllegalStateException("Module " + modName + " mod.json must contain a \"main\" field");
    }
    Boolean worker = json.getBoolean("worker");
    if (worker == null) {
      throw new IllegalStateException("Module " + modName + " mod.json must contain a \"worker\" field");
    }

    verticleManager.deploy(worker, deployName, main, config, urls.toArray(new URL[urls.size()]), instances, doneHandler);
  }
}
