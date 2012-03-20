var cluster = require('cluster');
var http = require('http');
//var numCPUs = require('os').cpus().length;
var numCPUs = 8;

if (cluster.isMaster) {
  console.log("Master starting");
  // Fork workers.
  for (var i = 0; i < numCPUs; i++) {
    cluster.fork();
  }

  cluster.on('death', function(worker) {
    console.log('worker ' + worker.pid + ' died');
  });
} else {
  console.log("Child starting");
  // Worker processes have a http server.
  http.Server(function(req, res) {
    res.end();
  }).listen(8080);
}