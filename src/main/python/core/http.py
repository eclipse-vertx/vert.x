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

import org.vertx.java.deploy.impl.VertxLocator
import org.vertx.java.core.Handler

class HttpServer:
    def __init__(self):
        self.http_server = org.vertx.java.deploy.impl.VertxLocator.vertx.createHttpServer()

    def request_handler(self, f):
        self.http_server.requestHandler(RequestHandler(f))

    def listen(self, port, host=None):
        if host is None:
            self.http_server.listen(port)
        else:
            self.http_server.listen(port, host)


class HttpServerRequest:
    def __init__(self, http_server_request):
        self.http_server_request = http_server_request
        self.http_server_response = HttpServerResponse(http_server_request.response)

    @property
    def method(self):
        return self.http_server_request.method

    @property
    def uri(self):
        return self.http_server_request.uri   

    @property
    def path(self):
        return self.http_server_request.path    

    @property
    def query(self):
        return self.http_server_request.query

    @property
    def params(self):
        if not self.params:
            self.params = http_server_request.params
        return self.params

    @property
    def response(self):
        return self.http_server_response

    @property
    def headers(self):
        if not self.headers:
            self.headers = http_server_request.headers
        return self.headers

    def body_handler(self, handler):
        self.http_server_request.bodyHandler(handler)

class HttpServerResponse:
    def __init__(self, http_server_response):
        self.http_server_response = http_server_response

    def end(self, data=None):
        if data is None:
            self.http_server_response.end()
        else:
            self.http_server_response.end(data)


      
class RequestHandler(org.vertx.java.core.Handler):
    def __init__(self, handler):
        self.handler = handler
        return

    def handle(self, req):
        self.handler(HttpServerRequest(req))

