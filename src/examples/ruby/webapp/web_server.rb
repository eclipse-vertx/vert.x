require('vertx')
include Vertx

@server = HttpServer.new
@server.ssl = true
@server.key_store_path = 'server-keystore.jks'
@server.key_store_password = 'wibble'

# Serve the static resources
@server.request_handler do |req|
  if req.path == '/'
    req.response.send_file('web/index.html')
  elsif !req.path.include?('..')
    req.response.send_file('web' + req.path)
  else
    req.response.status_code = 404
    req.response.end
  end
end

# Link up the client side to the server side event bus
Vertx::SockJSBridge.new(@server, {'prefix' => '/eventbus'},
 [
    # Allow calls to get static album data from the persistor
    {
      'address' => 'demo.persistor',
      'match' => {
        'action' => 'find',
        'collection' => 'albums'
      }
    },
    # Allow user to login
    {
      'address' => 'demo.authMgr.login'
    },
    # Let through orders posted to the order manager
    {
      'address' => 'demo.orderMgr'
    }
  ])

@server.listen(8080, 'localhost')

def vertx_stop 
  server.close
end
