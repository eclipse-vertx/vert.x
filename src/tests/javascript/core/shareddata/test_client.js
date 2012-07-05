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

function testMap() {
  var map1 = vertx.getMap("foo");
  tu.azzert(typeof map1 != undefined);
  var map2 = vertx.getMap("foo");
  tu.azzert(typeof map2 != undefined);
  tu.azzert(map1 === map2);
  var map3 = vertx.getMap("bar");
  tu.azzert(typeof map3 != undefined);
  tu.azzert(map3 != map2);

  var key = "blah";

  map1.put(key, 123);
  var val = map1.get(key);
  tu.azzert(typeof val === 'number');
  tu.azzert(val === 123);

  map1.put(key, 1.2345);
  var val = map1.get(key);
  tu.azzert(typeof val === 'number');
  tu.azzert(val === 1.2345);

  map1.put(key, "quux");
  var val = map1.get(key);
  tu.azzert(typeof val === 'string');
  tu.azzert(val === "quux");

  map1.put(key, true);
  var val = map1.get(key);
  tu.azzert(typeof val === 'boolean');
  tu.azzert(val === true);

  map1.put(key, false);
  var val = map1.get(key);
  tu.azzert(typeof val === 'boolean');
  tu.azzert(val === false);

  //Most testing done in Java tests

  tu.testComplete();
}

function testSet() {

  var set1 = vertx.getSet("foo");
  tu.azzert(typeof set1 != undefined);
  var set2 = vertx.getSet("foo");
  tu.azzert(typeof set2 != undefined);
  tu.azzert(set1 === set2);
  var set3 = vertx.getMap("bar");
  tu.azzert(typeof set3 != undefined);
  tu.azzert(set3 != set2);

  var key = "blah";

  set1.add(123);
  var val = set1.iterator().next();
  tu.azzert(typeof val === 'number');
  tu.azzert(val === 123);
  set1.clear();

  set1.add(1.2345);
  var val = set1.iterator().next();
  tu.azzert(typeof val === 'number');
  tu.azzert(val === 1.2345);
  set1.clear();

  set1.add("quux");
  var val = set1.iterator().next();
  tu.azzert(typeof val === 'string');
  tu.azzert(val === "quux");
  set1.clear();

  set1.add(true);
  var val = set1.iterator().next();
  tu.azzert(typeof val === 'boolean');
  tu.azzert(val === true);
  set1.clear();

  set1.add(false);
  var val = set1.iterator().next();
  tu.azzert(typeof val === 'boolean');
  tu.azzert(val === false);

  //Most testing done in Java tests

  tu.testComplete();
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}