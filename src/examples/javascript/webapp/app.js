/*
This verticle contains the configuration for our application and co-ordinates
start-up of the verticles that make up the application.
 */

load('vertx.js');

var log = vertx.logger;

// Our application config

var app_conf = {
  persistor_conf: {
    address: 'demo.persistor',
    db_name: 'test_db'
  },
  auth_mgr_conf: {
    address: 'demo.authMgr',
    user_collection: 'users',
    persistor_address: 'demo.persistor'
  },
  mailer_conf: {
    address: 'demo.mailer'
    /*
    Uncomment this to use a gmail account
    ,
    host: 'smtp.googlemail.com',
    port: 465,
    ssl: true,
    auth: true,
    username: 'your_username',
    password: 'your_password'
    */
  }
}

// Deploy the busmods

vertx.deployWorkerVerticle('busmods/mongo_persistor.js', app_conf.persistor_conf, 1, function() {
  load('static_data.js');
});
vertx.deployVerticle('busmods/auth_mgr.js', app_conf.auth_mgr_conf);
vertx.deployWorkerVerticle('busmods/mailer.js', app_conf.mailer_conf);


// Start the order manager

vertx.deployVerticle('order_mgr.js');

// Start the web server

vertx.deployVerticle('web_server.js');