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

Vertx::go do
  pool = RedisPool.new
  key = Buffer.create("my_count")
  HttpServer.new.request_handler do |req|
    if req.uri == "/"
      conn = pool.connection
      conn.incr(key).handler{ |future|
        req.response.put_header("Content-Type", "text/html; charset=UTF-8").
            write_str_and_end("<html><body><h1>Hit count is #{future.result}</h1></body></html>")
        conn.close
      }.execute
    else
      req.response.status_code = 404
      req.response.end
    end
  end.listen(8080)
end

puts "hit enter to exit"
STDIN.gets