require('vertx')

# Our application config - you can maintain it here or alternatively you could
# stick it in a conf.json text file and specify that on the command line when
# starting this verticle

# Configuration for the web server
web_server_conf = {

  # Normal web server stuff

  'port' => 8080,
  'host' => 'localhost',
  'ssl' => true,

  # Configuration for the event bus client side bridge
  # This bridges messages from the client side to the server side event bus
  'bridge' => true,

  # This defines which messages from the client we will let through
  # to the server side
  'inbound_permitted' => [
    # Allow calls to login
    {
      'address' => 'vertx.basicauthmanager.login'
    },
    # Allow calls to get static album data from the persistor
    {
      'address' => 'vertx.mongopersistor',
      'match' => {
        'action' => 'find',
        'collection' => 'albums'
      }
    },
    # And to place orders
    {
      'address' => 'vertx.mongopersistor',
      'requires_auth' => true,  # User must be logged in to send let these through
      'match' => {
        'action' => 'save',
        'collection' => 'orders'
      }
    }
  ],

  # This defines which messages from the server we will let through to the client
  'outbound_permitted' => [
    {}
  ]
}

# Now we deploy the modules that we need

# Deploy a MongoDB persistor module

Vertx.deploy_module('vertx.mongo-persistor-v1.2') do

  # And when it's deployed run a script to load it with some reference
  # data for the demo
  load('static_data.rb')
end

# Deploy an auth manager to handle the authentication

Vertx.deploy_module('vertx.auth-mgr-v1.1')

# Start the web server, with the config we defined above

Vertx.deploy_module('vertx.web-server-v1.0', web_server_conf)
