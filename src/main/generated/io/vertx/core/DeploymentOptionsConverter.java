/*
 * Copyright 2014 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package io.vertx.core;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;

/**
 * Converter for {@link io.vertx.core.DeploymentOptions}.
 *
 * NOTE: This class has been automatically generated from the {@link io.vertx.core.DeploymentOptions} original class using Vert.x codegen.
 */
public class DeploymentOptionsConverter {

  public static void fromJson(JsonObject json, DeploymentOptions obj) {
    if (json.getValue("config") instanceof JsonObject) {
      obj.setConfig(((JsonObject)json.getValue("config")).copy());
    }
    if (json.getValue("extraClasspath") instanceof JsonArray) {
      java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
      json.getJsonArray("extraClasspath").forEach( item -> {
        if (item instanceof String)
          list.add((String)item);
      });
      obj.setExtraClasspath(list);
    }
    if (json.getValue("ha") instanceof Boolean) {
      obj.setHa((Boolean)json.getValue("ha"));
    }
    if (json.getValue("instances") instanceof Number) {
      obj.setInstances(((Number)json.getValue("instances")).intValue());
    }
    if (json.getValue("isolatedClasses") instanceof JsonArray) {
      java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
      json.getJsonArray("isolatedClasses").forEach( item -> {
        if (item instanceof String)
          list.add((String)item);
      });
      obj.setIsolatedClasses(list);
    }
    if (json.getValue("isolationGroup") instanceof String) {
      obj.setIsolationGroup((String)json.getValue("isolationGroup"));
    }
    if (json.getValue("maxWorkerExecuteTime") instanceof Number) {
      obj.setMaxWorkerExecuteTime(((Number)json.getValue("maxWorkerExecuteTime")).longValue());
    }
    if (json.getValue("multiThreaded") instanceof Boolean) {
      obj.setMultiThreaded((Boolean)json.getValue("multiThreaded"));
    }
    if (json.getValue("worker") instanceof Boolean) {
      obj.setWorker((Boolean)json.getValue("worker"));
    }
    if (json.getValue("workerPoolName") instanceof String) {
      obj.setWorkerPoolName((String)json.getValue("workerPoolName"));
    }
    if (json.getValue("workerPoolSize") instanceof Number) {
      obj.setWorkerPoolSize(((Number)json.getValue("workerPoolSize")).intValue());
    }
  }

  public static void toJson(DeploymentOptions obj, JsonObject json) {
    if (obj.getConfig() != null) {
      json.put("config", obj.getConfig());
    }
    if (obj.getExtraClasspath() != null) {
      json.put("extraClasspath", new JsonArray(
          obj.getExtraClasspath().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    json.put("ha", obj.isHa());
    json.put("instances", obj.getInstances());
    if (obj.getIsolatedClasses() != null) {
      json.put("isolatedClasses", new JsonArray(
          obj.getIsolatedClasses().
              stream().
              map(item -> item).
              collect(java.util.stream.Collectors.toList())));
    }
    if (obj.getIsolationGroup() != null) {
      json.put("isolationGroup", obj.getIsolationGroup());
    }
    json.put("maxWorkerExecuteTime", obj.getMaxWorkerExecuteTime());
    json.put("multiThreaded", obj.isMultiThreaded());
    json.put("worker", obj.isWorker());
    if (obj.getWorkerPoolName() != null) {
      json.put("workerPoolName", obj.getWorkerPoolName());
    }
    json.put("workerPoolSize", obj.getWorkerPoolSize());
  }
}