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
def eb = vertx.eventBus

def pa = 'vertx.mongopersistor'

def albums = [
  [
    'artist': 'The Wurzels',
    'genre': 'Scrumpy and Western',
    'title': 'I Am A Cider Drinker',
    'price': 0.99
  ],
  [
    'artist': 'Vanilla Ice',
    'genre': 'Hip Hop',
    'title': 'Ice Ice Baby',
    'price': 0.01
  ],
  [
    'artist': 'Ena Baga',
    'genre': 'Easy Listening',
    'title': 'The Happy Hammond',
    'price': 0.50
  ],
  [
    'artist': 'The Tweets',
    'genre': 'Bird related songs',
    'title': 'The Birdy Song',
    'price': 1.20
  ]
]

// First delete everything

eb.send(pa, ['action': 'delete', 'collection': 'albums', 'matcher': [:]])

eb.send(pa, ['action': 'delete', 'collection': 'users', 'matcher': [:]])

// Insert albums - in real life price would probably be stored in a different collection, but, hey, this is a demo.

for (album in albums) {
  eb.send(pa, [
    'action': 'save',
    'collection': 'albums',
    'document': album
  ])
}

// And a user

eb.send(pa, [
  'action': 'save',
  'collection': 'users',
  'document': [
    'firstname': 'Tim',
    'lastname': 'Fox',
    'email': 'tim@localhost.com',
    'username': 'tim',
    'password': 'password'
  ]
])