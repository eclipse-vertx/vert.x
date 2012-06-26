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

var handler = function(msg, replier) {
  tu.azzert(msg.foo === 'wibble');

  // Trying to create any network clients or servers should fail - workers can only use the event bus

  try {
    vertx.createNetServer();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

   try {
    vertx.createNetClient();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

   try {
    vertx.createHttpServer();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

   try {
    vertx.createHttpClient();
    tu.azzert(false, "Should throw exception");
  } catch (err) {
    // OK
  }

  replier({eek: 'blurt'});
};

vertx.eventBus.registerHandler('testWorker', handler);

tu.appReady();

function vertxStop() {
  vertx.eventBus.unregisterHandler('testWorker', handler);
  tu.appStopped();
}
