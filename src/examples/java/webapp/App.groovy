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

package webapp

// Our application config - you can maintain it here or alternatively you could
// stick it in a app.json text file and specify that when starting the verticle

// Configuration for the persistor
def persistorConf = [
  address: 'demo.persistor',  // The event bus address at which the persistor listens
  db_name: 'test_db'          // The name of the MongoDB database
]

// Configuration for the web server
def webServerConf = [

  // Normal web server stuff

  port: 8080,
  host: 'localhost',
  ssl: true,

  // Configuration for the event bus client side bridge
  // This bridges messages from the client side to the server side event bus
  bridge: true,

  // Address of persistor - the user/password info is stored in the database
  // so we need this so the bridge can handle logins
  persistor_address: 'demo.persistor',

  // This defines which messages from the client we will let through
  permitted: [
    // Allow calls to get static album data from the persistor
    [
      'address' : 'demo.persistor',
      'match' : [
        'action' : 'find',
        'collection' : 'albums'
      ]
    ],
    // And to place orders
    [
      'address' : 'demo.persistor',
      'requires_auth' : true,  // User must be logged in to send let these through
      'match' : [
        'action' : 'save',
        'collection' : 'orders'
      ]
    ]
  ]
]

// Now we deploy the modules that we need

container.with {

  // Deploy a MongoDB persistor module

  deployVerticle('mongo-persistor', persistorConf, 1, {

    // And when it's deployed run a script to load it with some reference
    // data for the demo

    deployVerticle('StaticData.groovy')
  })

  // Start the web server, with the config we defined above

  deployVerticle('web-server', webServerConf)

}