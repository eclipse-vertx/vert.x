load('vertx.js')

var client = vertx.createHttpClient().setPort(8080);
var request = client.put('/', function(resp) {
  stdout.println("Got response " + resp.statusCode);
  resp.bodyHandler(function(body) {
    stdout.println("Got data " + body);
  })
});

request.setChunked(true)

for (var i = 0; i < 10; i++) {
  request.write("client-chunk-" + i);
}
request.end();