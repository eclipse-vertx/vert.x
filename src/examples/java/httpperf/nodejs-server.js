// Run this using node.js to compare the vert.x server to the node.js server
// node nodejs-server.js
var http = require('http');
var fs = require('fs');

http.createServer(function (req, res) {
  // res.end();
  
  fs.readFile("foo.html", "binary", function(err, file) { 
     if (err) {
        console.error(err);
     } else {   
         res.writeHead(200, {"Content-Type": "text/html", "Content-Length": file.length});
         res.write(file, "binary");
         res.end();
     }
  });
  
}).listen(8080, 'localhost');
