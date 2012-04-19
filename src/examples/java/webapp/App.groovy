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
def authMgrConf = [
  address: 'demo.authMgr',
  user_collection: 'users',
  persistor_address: 'demo.persistor'
]

def permitted =
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

container.with {

  // Deploy the busmods

  deployVerticle('mongo-persistor', persistorConf, 1, {
    deployVerticle('StaticData.groovy')
  })
  deployVerticle('auth-mgr', authMgrConf)

  // Start the order manager

  deployVerticle('org.vertx.java.examples.webapp.OrderMgr')

  // Start the web server

  deployVerticle('org.vertx.java.examples.webapp.WebServer', ['permitted': permitted])

}