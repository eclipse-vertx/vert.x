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

server = vertx.create_http_server()

# Serve the index page
@server.request_handler
def request_handler(req):
    if req.uri == "/":
        req.response.send_file("sockjs/index.html")

sjs_server = vertx.create_sockjs_server(server)

# The handler for the SockJS app - we just echo data back
def sock_handler(sock):
    def data_handler(buff):
        sock.write_buffer(buff)
    sock.data_handler(data_handler)

sjs_server.install_app( {"prefix": "/testapp"}, sock_handler)

server.listen(8080)