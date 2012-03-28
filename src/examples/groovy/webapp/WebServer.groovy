import org.vertx.groovy.core.http.HttpServer
import org.vertx.groovy.core.eventbus.SockJSBridge
import org.vertx.java.core.sockjs.AppConfig

/*
* Copyright 2011-2012 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/

def server =
  new HttpServer(SSL: true, keyStorePath: 'server-keystore.jks', keyStorePassword: 'wibble')

// Serve the static resources
server.requestHandler { req ->
  if (req.getPath() == '/') {
    req.response.sendFile('web/index.html')
  } else if (req.getPath().indexOf('..') == -1) {
    req.response.sendFile("web/${req.getPath()}")
  } else {
    req.response.setStatusCode(404)
    req.response.end()
  }
}

new SockJSBridge(server, new AppConfig().setPrefix('/eventbus'),
  [
    // Allow calls to get static album data from the persistor
    [
      'address' : 'demo.persistor',
      'match' : [
        'action' : 'find',
        'collection' : 'albums'
      ]
    ],
    // Allow user to login
    [
      'address' : 'demo.authMgr.login'
    ],
    // Let through orders posted to the order manager
    [
      'address' : 'demo.orderMgr'
    ]
  ]
)

server.listen(8080, 'localhost')

def vertxStop() {
  server.close()
}

