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

load('vertx.js')

var client = vertx.createNetClient().setSSL(true).setTrustAll(true);

client.connect(1234, function(sock) {
  stdout.println("connected");

  sock.dataHandler(function(buffer) {
    stdout.println("client receiving " + buffer.toString());
  });

  // Now send some data
  for (var i = 0; i < 10; i++) {
    var str = "hello" + i + "\n";
    stdout.println("Net client sending: " + str);
    sock.write(new vertx.Buffer(str));
  }
});


