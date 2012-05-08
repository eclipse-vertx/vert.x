var cluster = require('cluster');
var http = require('http');
var fs = require('fs');
var numCPUs = 6;

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
    //res.end();
    
   fs.readFile("foo.html", "binary", function(err, file) { 
     if (err) {
        console.error(err);
     } else {   
         res.writeHead(200, {"Content-Type": "text/html", "Content-Length": file.length});
         res.write(file, "binary");
         res.end();
     }
  });
  }).listen(8080);
}
