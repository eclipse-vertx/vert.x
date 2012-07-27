load("vertx.js")

console.log("in test-mod1");

vertx.createHttpServer().requestHandler(function(req) {
  req.response.end("<html><body><h1>Hello from vert.x!</h1></body></html>");
}).listen(8080, 'localhost');