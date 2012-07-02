import vertx

# Our application config - you can maintain it here or alternatively you could
# stick it in a conf.json text file and specify that on the command line when
# starting this verticle

# Configuration for the web server
web_server_conf = {

    # Normal web server stuff
    'port': 8080,
    'host': 'localhost',
    'ssl': True,

    # Configuration for the event bus client side bridge
    # This bridges messages from the client side to the server side event bus
    'bridge': True,

    # This defines which messages from the client we will let through
    # to the server side
    'inbound_permitted':  [
        # Allow calls to login
        {
            'address': 'vertx.basicauthmanager.login'
        },
        # Allow calls to get static album data from the persistor
        {
            'address': 'vertx.mongopersistor',
            'match': {
                'action': 'find',
                'collection': 'albums'
            }
        },
        # And to place orders
        {
            'address': 'vertx.mongopersistor',
            'requires_auth': True,  # User must be logged in to send let these through
            'match': {
                'action': 'save',
                'collection': 'orders'
            }
        }
    ],

    # This defines which messages from the server we will let through to the client
    'outbound_permitted': [
        {}
    ]
}

# And when it's deployed run a script to load it with some reference
# data for the demov
def deploy_handler(id):    
    execfile('static_data.py')

# Now we deploy the modules that we need
# Deploy a MongoDB persistor module
vertx.deploy_module('mongo-persistor-v1.0', handler=deploy_handler)

# Deploy an auth manager to handle the authentication
vertx.deploy_module('auth-mgr-v1.0')

# Start the web server, with the config we defined above
vertx.deploy_module('web-server-v1.0', web_server_conf)
