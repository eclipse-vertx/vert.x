load('vertx.js');

// The application config

var app_conf = {
  auth_mgr_conf: {
    address: 'demo.authMgr',
    user_collection: 'users',
    persistor_address: 'demo.persistor'
  },
  mailer_conf: {
    address: 'demo.mailer'
    /*
    Uncomment this if you want to use a gmail account
    ,
    host: 'smtp.googlemail.com',
    port: 465,
    ssl: true,
    auth: true,
    username: 'username',
    password: 'password'
    */
  },
  persistor_conf: {
    address: 'demo.persistor',
    db_name: 'test_db'
  },
  order_queue_conf: {
    address: 'demo.orderQueue'
  }
}

// Deploy the busmods

vertx.deployWorkerVerticle('busmods/mailer.js', app_conf.mailer_conf);
vertx.deployWorkerVerticle('busmods/persistor.js', app_conf.persistor_conf);
vertx.deployVerticle('busmods/auth_mgr.js', app_conf.auth_mgr_conf);
vertx.deployVerticle('busmods/work_queue.js', app_conf.order_queue_conf);

// Deploy our own verticles

vertx.deployVerticle('order_mgr.js');
vertx.deployVerticle('order_processor.js', null, 10);

// Start the web server

vertx.deployVerticle('web_server.js');

// Load some static data

// Currently we do this on a timer, since deployment is async, we need to make sure
// the persistor has actually started before we deploy the static data

vertx.setTimer(500, function() {
  load('static_data.js');
});

