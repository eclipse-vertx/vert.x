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

var user = 'tim@localhost';

function testMailer() {

  var msg = {
    from: user,
    to: user,
    subject: 'this is the subject',
    body: 'this is the body'
  }

  eb.send("test.mailer", msg, function(msg) {
    tu.azzert(msg.status == 'ok');
    tu.testComplete();
  });
}

function testMailerError() {
  var msg = {
    from: "wdok wdqwd qd",
    to: user,
    subject: 'this is the subject',
    body: 'this is the body'
  }

  eb.send("test.mailer", msg, function(msg) {
    tu.azzert(msg.status == 'error');
    tu.testComplete();
  });
}

tu.registerTests(this);

var mailerConfig = {address: 'test.mailer'}
var mailerID = vertx.deployWorkerVerticle('mailer', mailerConfig, 1, function() {
  tu.appReady();
});

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}