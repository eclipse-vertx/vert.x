load('vertx.js')

var client = new vertx.HttpClient().setPort(8080);
client.getNow('/', function(resp) {
  stdout.println("Got response " + resp.statusCode);
  resp.bodyHandler(function(body) {
    stdout.println("Got data " + body);
  })
});

function vertxStop() {
  client.close();
}