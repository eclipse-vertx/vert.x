load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();

function testDelimited() {

  var lineCount = 0;

  var numLines = 3;

  var output = function(line) {
    if (++lineCount == numLines) {
      tu.testComplete();
    }
  }

  var rp = new vertx.parsetools.DelimitedParser('\n', output);

  var input = "qwdqwdline1\nijijiline2\njline3\n";

  var buffer = new vertx.Buffer(input);

  rp.handle(buffer);
}

function testFixed() {

  var chunkCount = 0;

  var numChunks = 3;

  var chunkSize = 100;

  var output = function(chunk) {
    tu.azzert(chunk.length() == chunkSize);
    if (++chunkCount == numChunks) {
      tu.testComplete();
    }
  }

  var rp = new vertx.parsetools.FixedParser(chunkSize, output);

  var input = new vertx.Buffer(0);
  for (var i = 0; i < numChunks; i++) {
    var buff = tu.generateRandomBuffer(chunkSize);
    input.appendBuffer(buff);
  }

  rp.handle(input);
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
}