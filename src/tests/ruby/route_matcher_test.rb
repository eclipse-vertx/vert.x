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

require 'test/unit'
require 'vertx'
require 'utils'
include Vertx

class RouteMatcherTest < Test::Unit::TestCase

  def test_foo

  end

  def test_route_with_pattern
    params = { "name" => "foo", "version" => "v0.1"}
    route(false, "/:name/:version", params, "/foo/v0.1")
  end

  def test_route_with_regex
    params = { "param0" => "foo", "param1" => "v0.1"}
    regex = "\\/([^\\/]+)\\/([^\\/]+)"
    route(true, regex, params, "/foo/v0.1")
  end

  def route(regex, pattern, params, uri)

    latch = Utils::Latch.new(1)

    Vertx::go {

      handler = Proc.new do |req|

        assert(req.params.size == params.size)

        params.each do |k, v|
          assert(v == req.params[k])
        end

        req.response.end
      end

      rm = RouteMatcher.new

      if regex
        rm.get_re(pattern, handler)
      else
        rm.get(pattern, handler)
      end

      server = HttpServer.new
      server.request_handler(rm)
      server.listen(8080)

      client = HttpClient.new
      client.port = 8080;

      req = client.get(uri) do |resp|
        assert(200 == resp.status_code)

        server.close do
          client.close
          latch.countdown
        end
      end.end
    }

    assert(latch.await(5))
  end


end
