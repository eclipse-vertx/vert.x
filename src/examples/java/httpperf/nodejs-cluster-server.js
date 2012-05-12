var cluster = require('cluster');
var http = require('http');
var fs = require('fs');
var util = require('util');
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

    // Test 1
    // res.end();

    // Test 2
//    fs.readFile("foo.html", function(err, file) {
//       if (err) {
//          console.error(err);
//       } else {
//           res.writeHead(200, {"Content-Type": "text/html", "Content-Length": file.length});
//           res.write(file);
//           res.end();
//       }
//    });

    // Alternatively using streams:
//    var filePath = "foo.html";
//    fs.stat(filePath, function(err, stat) {
//      res.writeHead(200, {"Content-Type": "text/html", "Content-Length": stat.size});
//      var readStream = fs.createReadStream(filePath);
//      readStream.pipe(res);
//    });

    // Alternatively let's be nice to the node guys and hardcoded the stat size - but it'll still be slow
//    var filePath = "foo.html";
//    res.writeHead(200, {"Content-Type": "text/html", "Content-Length": 87});
//    var readStream = fs.createReadStream(filePath);
//    readStream.pipe(res);

    // We can try with chunked transfer encoding too
    res.writeHead(200, {"Content-Type": "text/html", "Transfer-Encoding": "chunked"});
    fs.createReadStream("foo.html").pipe(res);


  }).listen(8080);
}
