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

function testLoginDeniedEmptyDB() {
  deleteAll();
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'denied');
    tu.testComplete();
  });
}

function testLoginDeniedNonMatchingOthers() {
  deleteAll();
  storeEntries({username: 'bob', password: 'wibble'},
               {username: 'jane', password: 'uhuwdh'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'denied');
    tu.testComplete();
  });
}

function testLoginDeniedWrongPassword() {
  deleteAll();
  storeEntries({username: 'bob', password: 'wibble'},
               {username: 'tim', password: 'bar'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'denied');
    tu.testComplete();
  });
}

function testLoginDeniedOtherUserWithSamePassword() {
  deleteAll();
  storeEntries({username: 'bob', password: 'foo'},
               {username: 'tim', password: 'bar'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'denied');
    tu.testComplete();
  });
}

function testLoginOKOneEntryInDB() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(typeof reply.sessionID != 'undefined');
    tu.testComplete();
  });
}

function testLoginOKMultipleEntryInDB() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'},
               {username: 'bob', password: 'uahuhd'},
               {username: 'jane', password: 'ijqiejoiwjqe'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(typeof reply.sessionID != 'undefined');
    tu.testComplete();
  });
}

function testValidateDeniedNotLoggedIn() {
  deleteAll();
  eb.send('test.authMgr.validate', {sessionID: 'uhiuhuhihu', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'denied');
    tu.testComplete();
  });
}

function testValidateDeniedInvalidSessionID() {
  deleteAll();
  eb.send('test.authMgr.validate', {sessionID: 'uhiuhuhihu', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'denied');
    tu.testComplete();
  });
}

function testValidateDeniedLoggedInWrongSessionID() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(typeof reply.sessionID != 'undefined');
    eb.send('test.authMgr.validate', {sessionID: 'uhiuhuhihu', password: 'foo'}, function(reply) {
      tu.azzert(reply.status === 'denied');
      tu.testComplete();
    });
  });
}

function testValidateDeniedLoggedOut() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    var sessionID = reply.sessionID;
    eb.send('test.authMgr.logout', {sessionID: sessionID}, function(reply) {
      tu.azzert(reply.status === 'ok');
      eb.send('test.authMgr.validate', {sessionID: sessionID}, function(reply) {
        tu.azzert(reply.status === 'denied');
        tu.testComplete();
      });
    });
  });
}

function testValidateOK() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    tu.azzert(typeof reply.sessionID != 'undefined');
    var sessionID = reply.sessionID;
    eb.send('test.authMgr.validate', {sessionID: sessionID, password: 'foo'}, function(reply) {
      tu.azzert(reply.status === 'ok');
      tu.testComplete();
    });
  });
}

function testLoginMoreThanOnce() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    var sessionID = reply.sessionID;
    eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
      tu.azzert(reply.status === 'ok');
      // Should be different session ID
      var newSessionID = reply.sessionID;
      tu.azzert(newSessionID != sessionID);
      eb.send('test.authMgr.logout', {sessionID: sessionID}, function(reply) {
        tu.azzert(reply.status === 'error');
        tu.azzert(reply.message === 'Not logged in');
        eb.send('test.authMgr.validate', {sessionID: sessionID}, function(reply) {
          tu.azzert(reply.status === 'denied');
          eb.send('test.authMgr.validate', {sessionID: newSessionID}, function(reply) {
            tu.azzert(reply.status === 'ok');
            eb.send('test.authMgr.logout', {sessionID: newSessionID}, function(reply) {
              tu.azzert(reply.status === 'ok');
              eb.send('test.authMgr.validate', {sessionID: newSessionID}, function(reply) {
                tu.azzert(reply.status === 'denied');
                tu.testComplete();
              });
            });
          });
        });
      });
    });
  });
}

function testLoginMoreThanOnceThenLogout() {
  deleteAll();
  storeEntries({username: 'tim', password: 'foo'});
  eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
    tu.azzert(reply.status === 'ok');
    var sessionID = reply.sessionID;
    eb.send('test.authMgr.login', {username: 'tim', password: 'foo'}, function(reply) {
      tu.azzert(reply.status === 'ok');
      // Should be different session ID
      tu.azzert(reply.sessionID != sessionID);
      tu.testComplete();
    });
  });
}

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
var authMgrConfig = {address: 'test.authMgr', 'persistor_address' : 'test.persistor', 'user_collection': 'users'}
var authMgrID = null
vertx.deployVerticle('mongo-persistor', persistorConfig, 1, function() {
  authMgrID = vertx.deployVerticle('auth-mgr', authMgrConfig, 1, function() {
    tu.appReady();
  });
});

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}