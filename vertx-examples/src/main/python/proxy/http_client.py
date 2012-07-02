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

client = vertx.create_http_client()
client.port = 8080
client.host = "localhost"

def handle_body(body):
    print "Got data %s"% body

def handle_response(resp):
    print "Got response %s" % resp.status_code
    resp.body_handler(handle_body)

req = client.put("/someurl", handle_response)
req.chunked = True

for i in range(9):
  req.write_str("client-data-chunk-%d"% i)
req.end()