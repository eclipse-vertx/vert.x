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

function testPersistentWorkQueue() {

  var numMessages = 100;

  var count = 0;
  var doneHandler = function() {
    if (++count == numMessages) {
      eb.unregisterHandler("done", doneHandler);
      tu.testComplete();
    }
  };

  eb.registerHandler("done", doneHandler);

  for (var i = 0; i < numMessages; i++) {
    eb.send('test.orderQueue', {
      blah: "somevalue: " + i
    })
  }

}

function deleteAll() {
  eb.send('test.persistor', {
    collection: 'work',
    action: 'delete',
    matcher: {}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });
}

tu.registerTests(this);

var persistorConfig = {address: 'test.persistor', db_name: 'test_db'}
vertx.deployModule('mongo-persistor-v1.0', persistorConfig, 1, function() {
  deleteAll();
  var queueConfig = {address: 'test.orderQueue', persistor_address: 'test.persistor', collection: 'work'}
  vertx.deployModule('work-queue-v1.0', queueConfig, 1, function() {
    tu.appReady();
  });
});



function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}