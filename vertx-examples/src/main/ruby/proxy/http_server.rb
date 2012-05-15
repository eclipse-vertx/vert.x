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
require "set"
include Vertx

HttpServer.new.request_handler do |req|
  puts "Got request #{req.uri}"

  req.headers.each { |k, v| puts "#{k} : #{v}" }

  req.data_handler { |data| puts "Got data #{data}" }

  req.end_handler do
    # Now send back a response
    req.response.chunked = true

    for i in 0..9
      req.response.write_str("server-data-chunk-#{i}")
    end

    req.response.end
  end
end.listen(8282)