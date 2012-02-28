require('vertx')

# Our application config

app_conf = {
  'persistor_conf' => {
    'address' => 'demo.persistor',
    'db_name' => 'test_db'
  },
  'auth_mgr_conf' => {
    'address' => 'demo.authMgr',
    'user_collection' => 'users',
    'persistor_address' => 'demo.persistor'
  },
  'mailer_conf' => {
     'address' => 'demo.mailer'    
  }
}

# Deploy the busmods

Vertx.deploy_worker_verticle('busmods/mongo_persistor.rb', app_conf['persistor_conf']) do
    load('static_data.rb')
end

Vertx.deploy_verticle('busmods/auth_mgr.rb', app_conf['auth_mgr_conf'])

Vertx.deploy_worker_verticle('busmods/mailer.rb', app_conf['mailer_conf'])

# Start the order manager

Vertx.deploy_verticle('order_mgr.rb')

# Start the web server

Vertx.deploy_verticle('web_server.rb')
