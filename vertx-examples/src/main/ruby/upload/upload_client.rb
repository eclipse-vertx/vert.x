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
client.port = 8080
client.host = "localhost"
req = client.put("/someurl") { |resp| puts "Response #{resp.status_code}" }
filename = "upload/upload.txt"
FileSystem::props(filename) do |err, props|
  size = props.size
  req.put_header("Content-Length", size)
  FileSystem::open(filename) do |err, file|
    rs = file.read_stream
    pump = Pump.new(rs, req)
    rs.end_handler { req.end }
    pump.start
  end
end