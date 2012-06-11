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
from core.shared_data import SharedData
from core.event_bus import EventBus

conns = SharedData.get_set("conns")
server = vertx.create_net_server()

@server.connect_handler
def connect_handler(socket):
    conns.add(socket.write_handler_id)

    @socket.data_handler
    def data_handler(data):
        @conns.each
        def each(address):
            EventBus.send(address, data)

    @socket.closed_handler
    def closed_handler():
        conns.delete(socket.write_handler_id)

server.listen(1234)
