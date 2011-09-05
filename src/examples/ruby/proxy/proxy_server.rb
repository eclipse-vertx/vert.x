# Copyright 2002-2011 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not use
# this file except in compliance with the License. You may obtain a copy of the
# License at http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software distributed
# under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
# CONDITIONS OF ANY KIND, either express or implied. See the License for the
# specific language governing permissions and limitations under the License.

require "core/http"
require "core/nodex"
include Http

Nodex::go{
  client = Client.new
  client.port = 8282
  client.host = "localhost"

  Server.new.request_handler{ |req|
    puts "Proxying request: #{req.uri}"

    c_req = client.request(req.method, req.uri) { |c_res|
      puts "Proxying response: #{c_res.status_code}"
      req.response.status_code = c_res.status_code
      req.response.put_all_headers(c_res.headers)
      c_res.data_handler{ |data|
        puts "Proxying response body: #{data}"
        req.response.write_buffer(data);
      }
      c_res.end_handler{ req.response.end }
    }

    # c_req.chunked = true
    #req.response.chunked = true
    c_req.put_all_headers(req.headers)
    req.data_handler{ |data|
      puts "Proxying request body #{data}"
      c_req.write_buffer(data)
    }
    req.end_handler{ c_req.end }

  }.listen(8080)
}

puts "hit enter to exit"
STDIN.gets