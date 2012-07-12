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

@server.request_handler
def handle(req):
    print "Got request %s"% req.uri

    for header in req.headers.keys():
        print "%s : %s"% (header, req.headers[header])
    
    @req.data_handler
    def data_handler(data):
        print "Got data %s"% data

    @req.end_handler
    def end_handler(stream):
        req.response.chunked = True
        for i in range(9):
            req.response.write_str("server-data-chunk-%s"% i)
        req.response.end()

server.listen(8282)
