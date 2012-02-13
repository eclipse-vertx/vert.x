load('vertx.js');

log.println("in scratch");

foo();

function foo() {
  bar();
}

function bar() {
  quux();
}


function quux() {
  throw "foo"
}
