package org.vertx.java.platform.impl.ha.impl;

import org.vertx.java.core.Handler;
import org.vertx.java.core.impl.BlockingAction;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.platform.impl.PlatformManagerInternal;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.Queue;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
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
public class HAManager implements Handler<JsonObject> {

  private static final Logger log = LoggerFactory.getLogger(HAManager.class);

  private PlatformManagerInternal platformManager;
  private ClusterInfoManager cim;
  private JsonObject clusterInfo;
  private JsonArray haMods;
  private Queue<BlockingAction> toRun = new LinkedList<>();
  private boolean quorumAttained;

  public HAManager(final PlatformManagerInternal platformManager, ClusterInfoManager cim,
                   String group) {
    this.platformManager = platformManager;
    this.cim = cim;
    this.clusterInfo = new JsonObject();
    clusterInfo.putString("group", group != null ? group : "_DEFAULT_");
    this.haMods = new JsonArray();
    clusterInfo.putArray("mods", haMods);
    cim.crashHandler(this);
    cim.quorumHandler(new Handler<Boolean>() {
      public void handle(Boolean attained) {
        if (!attained) {
          log.warn("No Quorum. Node will shutdown!");
          platformManager.exit();
        } else {
          quorumAttained = true;
          checkRunTasks();
        }
      }
    });
    cim.update(clusterInfo);
  }

  public synchronized void runWhenQuorumAttained(BlockingAction action) {
    toRun.add(action);
    checkRunTasks();
  }

  private synchronized void checkRunTasks() {
    if (quorumAttained) {
      BlockingAction runner;
      while ((runner = toRun.poll()) != null) {
        runner.run();
      }
    }
  }

  public void simulateCrash() {
    cim.simulateCrash();
  }

  public void leave() {
    cim.leave();
  }

  public void addToHA(String moduleName, JsonObject conf, int instances, String group) {
    JsonObject moduleConf = new JsonObject().putString("module_name", moduleName);
    if (conf == null) {
      conf = new JsonObject();
    }
    moduleConf.putObject("conf", conf);
    moduleConf.putNumber("instances", instances);
    moduleConf.putString("group", group != null ? group : "_DEFAULT_");
    haMods.addObject(moduleConf);
    cim.update(clusterInfo);
  }

  public void removeFromHA(String moduleName, JsonObject conf, int instances) {
    Iterator<Object> iter = haMods.iterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      JsonObject mod = (JsonObject)obj;
      if (mod.getString("module_name").equals(moduleName) &&
          mod.getObject("conf").equals(conf) &&
          mod.getNumber("instances") == instances) {
        iter.remove();
      }
    }
    cim.update(clusterInfo);
  }

  @Override
  public void handle(final JsonObject moduleConf) {
    // App has failed over - start it!
    String moduleName = moduleConf.getString("module_name");
    log.info("Deploying module " + moduleName + " after failure of node");
    if (moduleName == null) {
      throw new IllegalStateException("No module name");
    }
    JsonObject conf = moduleConf.getObject("conf");
    String group = moduleConf.getString("group");
    int instances = (Integer)moduleConf.getNumber("instances", 1);
    platformManager.deployModule(moduleName, conf, instances, true, group, new Handler<String>() {
      @Override
      public void handle(String depID) {
        if (depID == null) {
          log.error("Failed to deploy module after failover");
        }
      }
    });

  }

  public String getNodeID() {
    return cim.getNodeID();
  }

}
