// Run this using node.js to compare the vert.x server to the node.js server
// node nodejs-server.js
var http = require('http');
var fs = require('fs');
var util = require('util');

http.createServer(function (req, res) {
  // Test 1
  // res.end();

  // Test 2
//  fs.readFile("foo.html", function(err, file) {
//     if (err) {
//        console.error(err);
//     } else {
//         res.writeHead(200, {"Content-Type": "text/html", "Content-Length": file.length});
//         res.write(file);
//         res.end();
//     }
//  });

  // Alternatively using streams:
//  var filePath = "foo.html";
//  fs.stat(filePath, function(err, stat) {
//    res.writeHead(200, {"Content-Type": "text/html", "Content-Length": stat.size});
//    var readStream = fs.createReadStream(filePath);
//    readStream.pipe(res);
//  });

  // Alternatively let's be nice to the node guys and hardcoded the stat size - but it'll still be slow
//  var filePath = "foo.html";
//  res.writeHead(200, {"Content-Type": "text/html", "Content-Length": 87});
//  var readStream = fs.createReadStream(filePath);
//  readStream.pipe(res);

  // We can try with chunked transfer encoding too
  res.writeHead(200, {"Content-Type": "text/html", "Transfer-Encoding": "chunked"});
  fs.createReadStream("foo.html").pipe(res);




}).listen(8080, 'localhost');
