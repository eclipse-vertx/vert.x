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

client = vertx.create_http_client(port=8282, host="localhost")
server = vertx.create_http_server()

@server.request_handler
def request_handler(req):
  print "Proxying request: %s"% req.uri
  def response_handler(c_res):
    print "Proxying response: %s"% c_res.status_code
    req.response.chunked = True
    req.response.status_code = c_res.status_code
    for header in c_res.headers.keys():
      req.response.put_header(header, c_res.headers[header])
    def data_handler(data):
      print "Proxying response body: %s"% data
      req.response.write_buffer(data)
    c_res.data_handler(data_handler)

    def end_handler(stream):
      req.response.end()
    c_res.end_handler(end_handler)

  c_req = client.request(req.method, req.uri, response_handler)
  c_req.chunked = True
  for header in req.headers.keys():
    c_req.put_header(header, req.headers[header])

  def data_handler(data):
    print "Proxying request body %s"% data
    c_req.write_buffer(data)

  req.data_handler(data_handler) 

  def end_handler(stream):
    c_req.end()

  req.end_handler(end_handler)

server.listen(8080)