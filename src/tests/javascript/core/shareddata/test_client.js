load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

function testMap() {
  var map1 = vertx.shareddata.getMap("foo");
  tu.azzert(typeof map1 != undefined);
  var map2 = vertx.shareddata.getMap("foo");
  tu.azzert(typeof map2 != undefined);
  tu.azzert(map1 === map2);
  var map3 = vertx.shareddata.getMap("bar");
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

  var set1 = vertx.shareddata.getSet("foo");
  tu.azzert(typeof set1 != undefined);
  var set2 = vertx.shareddata.getSet("foo");
  tu.azzert(typeof set2 != undefined);
  tu.azzert(set1 === set2);
  var set3 = vertx.shareddata.getMap("bar");
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