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

vertx.createNetServer().connectHandler(function(socket) {
  var parser = new vertx.createDelimitedParser("\n", function(line) {
    line = line.toString().replace(/\s+$/,""); // rtrim
    if (line.indexOf("subscribe,") == 0) {
      var topicName = line.split(",", 2)[1]
      stdout.println("subscribing to " + topicName);
      var topic = vertx.getSet(topicName);
      topic.add(socket.writeHandlerID)
    } else if (line.indexOf("unsubscribe,") == 0) {
      var topicName = line.split(",", 2)[1]
      stdout.println("unsubscribing from " + topicName);
      var topic = vertx.getSet(topicName)
      topic.remove(socket.writeHandlerID)
      if (topic.isEmpty()) {
        vertx.removeSet(topicName)
      }
     } else if (line.indexOf("publish,") == 0) {
      var sp = line.split(',', 3)
      stdout.println("publishing to " + sp[1] + " with " + sp[2]);
      var topic = vertx.getSet(sp[1])
      var tarr = topic.toArray();
      for (var i = 0; i < tarr.length; i++) {
        vertx.eventBus.send(tarr[i], new vertx.Buffer(sp[2]));
      }
    }
  });
  socket.dataHandler(parser)
}).listen(1234)
