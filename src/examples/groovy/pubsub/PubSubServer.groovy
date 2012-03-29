import org.vertx.groovy.core.net.NetServer
import org.vertx.groovy.core.parsetools.RecordParser
import org.vertx.java.core.shareddata.SharedData
import org.vertx.groovy.core.eventbus.EventBus
import org.vertx.groovy.core.buffer.Buffer

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

server = new NetServer().connectHandler { socket ->
  def parser = RecordParser.newDelimited("\n") { line ->
    line = line.toString().trim()
    if (line.startsWith("subscribe")) {
      def topicName = line.split(",", 2)[1]
      println "subscribing to ${topicName}"
      def topic = SharedData.instance.getSet(topicName)
      topic.add(socket.getWriteHandlerID())
    } else if (line.startsWith("unsubscribe")) {
      def topicName = line.split(",", 2)[1]
      println "unsubscribing from ${topicName}"
      def topic = SharedData.instance.getSet(topicName)
      topic.remove(socket.getWriteHandlerID())
      if (topic.isEmpty()) {
        SharedData.instance.removeSet(topicName)
      }
     } else if (line.startsWith("publish")) {
      def sp = line.split(',', 3)
      println "publishing to ${sp[1]} with ${sp[2]}"
      def topic = SharedData.instance.getSet(sp[1])
      def tarr = topic.toArray();
      for (i in 0 ..< tarr.length) {
        EventBus.instance.send(tarr[i], new Buffer(sp[2]))
      }
    }
  }
  socket.dataHandler(parser.toClosure())
}.listen(1234)

def vertxStop() {
  server.close()
}