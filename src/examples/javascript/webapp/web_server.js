load('vertx.js');

var server = new vertx.HttpServer()
    .setSSL(true)
    .setKeyStorePath('server-keystore.jks')
    .setKeyStorePassword('wibble');

// Serve the static resources
server.requestHandler(function(req) {
  if (req.path === '/') {
    req.response.sendFile('web/index.html');
  } else if (req.path.indexOf('..') === -1) {
    req.response.sendFile('web' + req.path);
  } else {
    req.response.statusCode = 404;
    req.response.end();
  }
})

new vertx.SockJSBridge(server, {prefix : '/eventbus'},
  [
    // Allow calls to get static album data from the persistor
    {
      address : 'demo.persistor',
      match : {
        action : 'find',
        collection : 'albums'
      }
    },
    // Allow user to login
    {
      address : 'demo.authMgr.login'
    },
    // Let through orders posted to the order manager
    {
      address : 'demo.orderMgr'
    }
  ]
);

server.listen(8080, 'localhost');

function vertxStop() {
  server.close();
}