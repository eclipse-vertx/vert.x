// Run this using node.js to compare the vert.x server to the node.js server
// node nodejs-server.js
var http = require('http');
http.createServer(function (req, res) {
  res.end();
}).listen(8080, 'localhost');