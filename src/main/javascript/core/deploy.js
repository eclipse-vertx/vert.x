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

var vertx = vertx || {};

if (!vertx.deployVerticle) {
  (function() {

    function Deploy(worker, main, config, instances, doneHandler) {
      if (!instances) instances = 1;
      if (config) {
        // Convert to Java Json Object
        var str = JSON.stringify(config);
        config = new org.vertx.java.core.json.JsonObject(str);
      } else {
        config = null;
      }
      if (!doneHandler) doneHandler = null;
      if (worker) {
        return org.vertx.java.core.Vertx.instance.deployWorkerVerticle(main, config, instances, doneHandler);
      } else {
        return org.vertx.java.core.Vertx.instance.deployVerticle(main, config, instances, doneHandler);
      }
    }

    vertx.deployVerticle = function(main, config, instances, doneHandler) {
      return Deploy(false, main, config, instances, doneHandler);
    }

    vertx.deployWorkerVerticle = function(main, config, instances, doneHandler) {
      return Deploy(true, main, config, instances, doneHandler);
    }

    vertx.undeployVerticle = function(name, doneHandler) {
      if (!doneHandler) doneHandler = null;
      org.vertx.java.core.Vertx.instance.undeployVerticle(name, doneHandler);
    }

    vertx.exit = function() {
       org.vertx.java.core.Vertx.instance.exit();
    }

    vertx.getConfig = function() {
      var j_conf = org.vertx.java.core.Vertx.instance.getConfig();
      return j_conf == null ? null : JSON.parse(j_conf.encode());
    }

  })();
}


