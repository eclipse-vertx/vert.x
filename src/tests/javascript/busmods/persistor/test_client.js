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

function deleteAll() {
  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'delete',
    matcher: {}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });
}

function testSave() {
  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'save',
    document: {
      name: 'tim',
      age: 40,
      pi: 3.14159,
      male: true,
      cheeses: ['brie', 'stilton']
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
    var id  = reply._id;
    tu.azzert(id != undefined);

    // Now update it
    eb.send('test.persistor', {
      collection: 'testcoll',
      action: 'save',
      document: {
        _id: id,
        name: 'tim',
        age: 1000
      }
    }, function(reply) {
      tu.azzert(reply.status === 'ok');

       eb.send('test.persistor', {
          collection: 'testcoll',
          action: 'findone',
          document: {
            _id: id
          }
        }, function(reply) {
          tu.azzert(reply.status === 'ok');
          tu.azzert(reply.result.name === 'tim');
          tu.azzert(reply.result.age === 1000);
          tu.testComplete();
       });
    });
  });
}

function testFind() {

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'save',
    document: {
      name: 'tim',
      age: 40,
      pi: 3.14159,
      male: true,
      cheeses: ['brie', 'stilton']
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'find',
    matcher: {
      name: 'tim'
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(reply.results.length === 1);
    var res = reply.results[0];
    tu.azzert(res.name === 'tim');
    tu.azzert(res.age === 40);
    tu.azzert(res.pi === 3.14159);
    tu.azzert(res.male === true);
    tu.azzert(res.cheeses.length === 2);
    tu.azzert(res.cheeses[0] === 'brie');
    tu.azzert(res.cheeses[1] === 'stilton');
    tu.azzert(res._id != undefined);
    tu.testComplete();
  });
}

function testFindOne() {

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'save',
    document: {
      name: 'tim',
      age: 40,
      pi: 3.14159,
      male: true,
      cheeses: ['brie', 'stilton']
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'findone',
    matcher: {
      name: 'tim'
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
    var res = reply.result;
    tu.azzert(res.name === 'tim');
    tu.azzert(res.age === 40);
    tu.azzert(res.pi === 3.14159);
    tu.azzert(res.male === true);
    tu.azzert(res.cheeses.length === 2);
    tu.azzert(res.cheeses[0] === 'brie');
    tu.azzert(res.cheeses[1] === 'stilton');
    tu.azzert(res._id != undefined);
    tu.testComplete();
  });
}

function testFindWithLimit() {

  var num = 20;

  var limit = 12;

  for (var i = 0; i < num; i++) {
    eb.send('test.persistor', {
      collection: 'testcoll',
      action: 'save',
      document: {
        name: 'tim' + i
      }
    }, function(reply) {
      tu.azzert(reply.status === 'ok');
    });
  }

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'find',
    limit: limit,
    matcher: {}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(reply.results.length === limit);
    tu.testComplete();
  });
}

function testFindWithSort() {

  var num = 10;

  for (var i = 0; i < num; i++) {
    eb.send('test.persistor', {
      collection: 'testcoll',
      action: 'save',
      document: {
        name: 'tim',
        age: Math.floor(Math.random()*11)
      }
    }, function(reply) {
      tu.azzert(reply.status === 'ok');
    });
  }

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'find',
    matcher: {},
    sort: {age: 1}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(reply.results.length === num);
    var last = 0;
    for (var i = 0; i < reply.results.length; i++) {
      var age = reply.results[i].age;
      tu.azzert(age >= last);
      last = age;
    }
    tu.testComplete();
  });
}

function testFindBatched() {

  var num = 103;

  for (var i = 0; i < num; i++) {
    eb.send('test.persistor', {
      collection: 'testcoll',
      action: 'save',
      document: {
        name: 'tim',
        age: i
      }
    }, function(reply) {
      tu.azzert(reply.status === 'ok');
    });
  }

  var received = 0;

  function createReplyHandler() {
    return function(reply, replier) {
      received += reply.results.length;
      if (received < num) {
        tu.azzert(reply.results.length === 10);
        tu.azzert(reply.status === 'more-exist');
        replier({}, createReplyHandler());
      } else {
        tu.azzert(reply.results.length === 3);
        tu.azzert(reply.status === 'ok');
        tu.testComplete();
      }
    }
  }

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'find',
    matcher: {},
    batch_size: 10
  }, createReplyHandler());
}

function testDelete() {

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'save',
    document: {
      name: 'tim'
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });
  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'save',
    document: {
      name: 'bob'
    }
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });

  eb.send('test.persistor', {
    collection: 'testcoll',
    action: 'delete',
    matcher: {
      name: 'tim'
    }
  }, function(reply) {

    tu.azzert(reply.status === 'ok');
    tu.azzert(reply.number === 1);

    eb.send('test.persistor', {
      collection: 'testcoll',
      action: 'find',
      matcher: {
        name: 'bob'
      }
    }, function(reply) {
      tu.azzert(reply.status === 'ok');
      tu.azzert(reply.results.length === 1);

      tu.testComplete();
    });
  });
}

tu.registerTests(this);
var persistorConfig = {address: 'test.persistor', db_name: 'test_db'}
vertx.deployModule('mongo-persistor-v1.0', persistorConfig, 1, function() {
  deleteAll();
  tu.appReady();
});

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}