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

load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.eventBus;

function testDeploy() {
  eb.registerHandler("test-handler", function MyHandler(message) {
    if ("started" === message) {
      eb.unregisterHandler("test-handler", MyHandler);
      tu.testComplete();
    }
  });
  vertx.deployVerticle("core/deploy/child.js");
}

function testUndeploy() {
  vertx.deployVerticle("core/deploy/child.js", null, 1, function(id) {
    tu.checkContext();
    eb.registerHandler("test-handler", function MyHandler(message) {
      if ("stopped" === message) {
        eb.unregisterHandler("test-handler", MyHandler);
        tu.testComplete();
      }
    });
    vertx.undeployVerticle(id);
  });
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}