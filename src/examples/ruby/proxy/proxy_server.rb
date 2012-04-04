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

require "vertx"
include Vertx

client = HttpClient.new
client.port = 8282
client.host = "localhost"

HttpServer.new.request_handler do |req|
  puts "Proxying request: #{req.uri}"

  c_req = client.request(req.method, req.uri) do |c_res|
    puts "Proxying response: #{c_res.status_code}"
    req.response.chunked = true
    req.response.status_code = c_res.status_code
    req.response.headers.update(c_res.headers)
    c_res.data_handler do |data|
      puts "Proxying response body: #{data}"
      req.response.write_buffer(data);
    end
    c_res.end_handler { req.response.end }
  end
  c_req.chunked = true
  c_req.headers.update(req.headers)
  req.data_handler do |data|
    puts "Proxying request body #{data}"
    c_req.write_buffer(data)
  end
  req.end_handler { c_req.end }

end.listen(8080)