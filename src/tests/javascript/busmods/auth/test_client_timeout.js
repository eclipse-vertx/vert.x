load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();
var eb = vertx.EventBus;

function testSessionTimeout() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(typeof reply.sessionID != 'undefined');
    var sessionID = reply.sessionID;
    eb.send('test.authMgr.validate', {sessionID: sessionID, password: 'foo'}, function(reply) {
      tu.azzert(reply.status === 'ok');
      // Allow session to timeout then try and validate again
      vertx.setTimer(750, function() {
        eb.send('test.authMgr.validate', {sessionID: sessionID, password: 'foo'}, function(reply) {
          tu.azzert(reply.status === 'denied');
          tu.testComplete();
        });
      });

    });
  });
}

function storeEntries() {
  for (var i = 0; i < arguments.length; i++) {
    var entry = arguments[i];
    eb.send('test.persistor', {
      collection: 'users',
      action: 'save',
      document: entry
    }, function(reply) {
      tu.azzert(reply.status === 'ok');
    });
  }
}

function deleteAll() {
  eb.send('test.persistor', {
    collection: 'users',
    action: 'delete',
    matcher: {}
  }, function(reply) {
    tu.azzert(reply.status === 'ok');
  });
}


tu.registerTests(this);

var persistorConfig = {address: 'test.persistor', 'db_name' : 'test_db'}
var authMgrConfig = {address: 'test.authMgr', 'persistor_address' : 'test.persistor', 'user_collection': 'users',
                     session_timeout: 500}
var authMgrID = null
var persistorID = vertx.deployWorkerVerticle('busmods/mongo_persistor.js', persistorConfig, 1, function() {
  authMgrID = vertx.deployWorkerVerticle('busmods/auth_mgr.js', authMgrConfig, 1, function() {
    tu.appReady();
  });
});

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
//  vertx.undeployVerticle(authMgrID, function() {
//    vertx.undeployVerticle(persistorID, function() {
//      tu.appStopped();
//    });
//  });
}