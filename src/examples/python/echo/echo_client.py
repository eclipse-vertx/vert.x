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
from core.buffer import Buffer

client = vertx.create_net_client(port=1234, host="localhost")

def connect_handler(socket):
    @socket.data_handler
    def data_handler(data):
        print "Echo client received %s"% data
    for i in range(10):
        print "Echo client sending %d"% i
        socket.write_buffer(Buffer.create_from_str("%d"% i))

client.connect(1234, "localhost", connect_handler)