load('vertx.js')

var server = new vertx.HttpServer();

server.requestHandler(function(req) {

  var headers = req.headers();

  var str = 'Headers:\n';
  for (var k in headers) {
    str = str.concat(k, ': ', headers[k], '\n');
  }

  var params = req.params();

  str = str.concat('\nParams:\n');
  for (var k in params) {
    str = str.concat(k, ': ', params[k], '\n');
  }

  log.println('method is ' + req.method);
  log.println('uri is ' + req.uri);
  log.println('path is ' + req.path);
  log.println('query is ' + req.query);

  req.bodyHandler(function(body) {
    log.println('Body received, length is ' + body.length());
  });

  req.response.putHeaders({ 'Content-Length' : '0', 'Some-Other-Header': 'abc'});

  req.response.setChunked(true);


  req.response.putTrailers({ 'Some-Trailer' : 'asdasd', 'Some-Other-Trailer': 'abc'});


  req.response.end(str);
})

server.listen(8181, 'localhost');

function vertxStop() {
  server.close
}
