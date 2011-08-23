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

require 'test/unit'
require 'core/nodex'
require 'core/http'
require 'utils'

class HttpTest < Test::Unit::TestCase

  def test_http

    latch1 = Utils::Latch.new(1)

    Nodex::go{
      server = Http::Server.new
      server.request_handler { |req|
        req.response.write_str("some response")
        req.response.end
      }
      server.listen(8080)

      client = Http::Client.new
      client.port = 8080;

      client.get_now("/someurl") { |resp|
        puts "Got response #{resp.status_code}"

        server.close{
          client.close
          latch1.countdown
        }
      }
    }

    assert(latch1.await(5))

  end
end