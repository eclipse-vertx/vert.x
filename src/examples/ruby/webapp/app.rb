require('vertx')

# Our application config

persistor_conf = {
  'address' => 'demo.persistor',
  'db_name' => 'test_db'
}
auth_mgr_conf = {
  'address' => 'demo.authMgr',
  'user_collection' => 'users',
  'persistor_address' => 'demo.persistor'
}

# Deploy the busmods

Vertx.deploy_verticle('mongo-persistor', persistor_conf) do
    load('static_data.rb')
end

Vertx.deploy_verticle('auth-mgr', auth_mgr_conf)

# Start the order manager

Vertx.deploy_verticle('order_mgr.rb')

# Start the web server

Vertx.deploy_verticle('web_server.rb')
