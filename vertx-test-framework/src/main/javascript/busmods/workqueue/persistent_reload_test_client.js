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

var numMessages = 10;

function testPersistentReloadWorkQueue() {

  var count = 0;
  var doneHandler = function() {
    if (++count == numMessages) {
      eb.unregisterHandler("done", doneHandler);
      tu.testComplete();
    }
  };

  eb.registerHandler("done", doneHandler);

  var persistorConfig = {address: 'test.persistor', db_name: 'test_db'}
  vertx.deployWorkerVerticle('mongo-persistor', persistorConfig, 1, function() {
    insertWork();
    var queueConfig = {address: 'test.orderQueue', persistor_address: 'test.persistor', collection: 'work'}
    vertx.deployWorkerVerticle('work-queue', queueConfig, 1, function() {
      vertx.deployVerticle('busmods/workqueue/order_processor.js', {dont_send_app_lifecycle: true}, 10);
    });
  });
}

function insertWork() {

  for (var i = 0; i < numMessages; i++) {

    eb.send('test.persistor', {
      collection: 'work',
      action: 'save',
      document: {
        blah: "foo" + i
      }
    });
  }
}

tu.registerTests(this);

tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}
