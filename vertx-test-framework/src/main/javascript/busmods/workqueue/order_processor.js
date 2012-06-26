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

load('vertx.js');
load('test_utils.js');

var tu = new TestUtils();

var eb = vertx.eventBus;

var id = vertx.generateUUID();

var dontsendAppLifeCycle = vertx.config.dont_send_app_lifecycle;

var handler = function(message, replier) {
  tu.azzert(message.blah != "undefined");
  replier({});
  eb.send('done', {});
};

eb.registerHandler(id, handler);

eb.send('test.orderQueue.register', {
  processor: id
}, function() {
  if (!dontsendAppLifeCycle) {
    tu.appReady();
  }
});

function vertxStop() {
  eb.send('test.orderQueue.unregister', {
    processor: id
  });
  eb.unregisterHandler(id, handler);
  if (!dontsendAppLifeCycle) {
    tu.appStopped();
  }
}



