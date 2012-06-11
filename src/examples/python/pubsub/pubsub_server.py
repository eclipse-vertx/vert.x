# Copyright 2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import vertx

from core.parsetools import RecordParser
from core.shared_data import SharedData
from core.event_bus import EventBus
from core.buffer import Buffer

server = vertx.create_net_server()

@server.connect_handler
def connect_handler(socket):
    def parser_handler(line):
        line = line.to_string().rstrip()
        if line.startswith("subscribe,"):
            topic_name = line.split(",", 2)[1]
            print "subscribing to %s"% topic_name
            topic = SharedData.get_set(topic_name)
            topic.add(socket.write_handler_id)
        elif line.startswith("unsubscribe,"):
            topic_name = line.split(",", 2)[1]
            print "unsubscribing from %s"% topic_name
            topic = SharedData.get_set(topic_name)
            topic.delete(socket.write_handler_id)
            if topic.empty:
                SharedData.remove_set(topic_name)
        elif line.startswith("publish,"):
            sp = line.split(',', 3)
            print "publishing to %s with %s"% (sp[1], sp[2])
            topic = SharedData.get_set(sp[1])
            print "topic is %s"% topic
            @topic.each
            def each(address):
                EventBus.send(address, Buffer.create_from_str(sp[2]))

    parser = RecordParser.new_delimited("\n", parser_handler)
    socket.data_handler(parser)
server.listen(1234)
