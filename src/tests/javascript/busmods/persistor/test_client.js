load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

var eb = vertx.EventBus;

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
    tu.testComplete();
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

    log.println("got reply " + JSON.stringify(reply));

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
var persistorID = vertx.deployWorkerVerticle('busmods/persistor.js', persistorConfig, 1, function() {
  deleteAll();
  tu.appReady();
});

function vertxStop() {
  tu.unregisterAll();
  vertx.undeployVerticle(persistorID, function() {
    tu.appStopped();
  });
}