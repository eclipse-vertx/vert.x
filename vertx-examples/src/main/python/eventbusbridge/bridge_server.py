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
from core.sock_js import SockJSServer

server = vertx.create_http_server()

@server.request_handler
def request_handler(req):
    if req.uri == "/":
        req.response.send_file("eventbusbridge/index.html") 
    elif req.uri == "/vertxbus.js":
        req.response.send_file("eventbusbridge/vertxbus.js")


# Create a bridge that lets everything through (be careful!)
SockJSServer(server).bridge({"prefix": "/eventbus"}, [{}], [{}])

server.listen(8080)
