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

// Our application config

def persistorConf = [
  address: 'demo.persistor',
  db_name: 'test_db'
]

def webServerConf = [
  port: 8080,
  host: 'localhost',
  ssl: true,
  bridge: true,
  persistor_address: 'demo.persistor',
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
      'requires_auth' : true,
      'match' : [
        'action' : 'save',
        'collection' : 'orders'
      ]
    ]
  ]
]

container.with {

  // Deploy the busmods

  deployVerticle('mongo-persistor', persistorConf, 1, {
    deployVerticle('StaticData.groovy')
  })

  // Start the web server

  deployVerticle('web-server', webServerConf)

}