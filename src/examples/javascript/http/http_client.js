load('vertx.js')

vertx.createHttpClient().setPort(8080).getNow('/', function(resp) {
  stdout.println("Got response " + resp.statusCode);
  resp.bodyHandler(function(body) {
    stdout.println("Got data " + body);
  })
});
