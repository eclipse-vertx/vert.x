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

function testOneOff() {
  var count = 0;
  var id = vertx.setTimer(1000, function(timer_id) {
    tu.checkContext();
    tu.azzert(id === timer_id);
    tu.azzert(count === 0);
    count++;
    setEndTimer();
  });
}

function testPeriodic() {
  var numFires = 10;
  var delay = 100;
  var count = 0;
  var id = vertx.setPeriodic(delay, function(timer_id) {
    tu.checkContext();
    tu.azzert(id === timer_id);
    count++;
    if (count === numFires) {
      vertx.cancelTimer(timer_id);
      setEndTimer();
    }
    if (count > numFires) {
      tu.azzert(false, "Fired too many times");
    }
  });
}

function setEndTimer() {
  vertx.setTimer(10, function() {
    tu.testComplete();
  })
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}