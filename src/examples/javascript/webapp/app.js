/*
This verticle contains the configuration for our application and co-ordinates
start-up of the verticles that make up the application.
 */

load('vertx.js');

// Our application config

var persistorConf =  {
  address: 'demo.persistor',
  db_name: 'test_db'
};
//var authMgrConf = {
//  address: 'demo.authMgr',
//  user_collection: 'users',
//  persistor_address: 'demo.persistor'
//};

// Deploy the busmods

vertx.deployVerticle('mongo-persistor', persistorConf, 1, function() {
  load('static_data.js');
});
//vertx.deployVerticle('auth-mgr', authMgrConf);

// Start the order manager

// vertx.deployVerticle('order_mgr.js');

// Start the web server

vertx.deployVerticle('web_server.js');