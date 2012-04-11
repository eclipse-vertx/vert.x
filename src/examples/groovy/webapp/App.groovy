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
def mailerConf = [
  address: 'demo.mailer'
  /*
  Uncomment this to use a gmail account
  ,
  host: 'smtp.googlemail.com',
  port: 465,
  ssl: true,
  auth: true,
  username: 'your_username',
  password: 'your_password'
  */
]

container.with {

  // Deploy the busmods

  deployWorkerVerticle('busmods/mongo_persistor.js', persistorConf, 1, {
    deployVerticle('StaticData.groovy')
  })
  deployVerticle('busmods/auth_mgr.js', authMgrConf)
  deployWorkerVerticle('busmods/mailer.js', mailerConf)

  // Start the order manager

  deployVerticle('OrderMgr.groovy')

  // Start the web server

  deployVerticle('WebServer.groovy')

}