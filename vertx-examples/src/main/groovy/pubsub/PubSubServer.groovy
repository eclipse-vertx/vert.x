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

import org.vertx.groovy.core.buffer.Buffer
import org.vertx.groovy.core.parsetools.RecordParser

vertx.createNetServer().connectHandler { socket ->
  def parser = RecordParser.newDelimited("\n") { line ->
    line = line.toString().trim()
    switch (line) {
    case ~/subscribe\s*,.*/:
      def topicName = line.split(",", 2)[1]
      println "subscribing to ${topicName}"
      def topic = vertx.sharedData.getSet(topicName)
      topic << socket.writeHandlerID
      break

    case ~/unsubscribe\s*,.*/:
      def topicName = line.split(",", 2)[1]
      println "unsubscribing from ${topicName}"
      def topic = vertx.sharedData.getSet(topicName)
      topic.remove(socket.writeHandlerID)
      if (topic.isEmpty()) {
        vertx.sharedData.removeSet(topicName)
      }
      break

    case ~/publish\s*,.*,.*/:
      def sp = line.split(',', 3)
      println "publishing to ${sp[1]} with ${sp[2]}"
      def topic = vertx.sharedData.getSet(sp[1])
      for (id in topic) {
        vertx.eventBus.send(id, new Buffer(sp[2]))
      }
      break

    default:
      println "Unknown command '${line}'"
      break
    }
  }
  socket.dataHandler(parser.toClosure())
}.listen(1234)
