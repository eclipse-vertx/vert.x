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

var client = new vertx.NetClient().setSendBufferSize(2048).setReceiveBufferSize(2048);

var numConns = 50000;

var received = 0;

connect(0);

function connect(num) {
  client.connect(1234, function(sock) {
    stdout.println("connected " + num);

    sock.dataHandler(function(buffer) {
      stdout.println("received " + received);
      received++;
    });

    sock.write("X");

    num++;
    if (num < numConns) {
      vertx.runOnLoop(function() { connect(num) });
    }
  });

}



