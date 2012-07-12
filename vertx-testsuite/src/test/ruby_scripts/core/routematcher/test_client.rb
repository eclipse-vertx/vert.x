# Copyright 2011-2012 the original author or authors.
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
require "test_utils"

@tu = TestUtils.new

@server = HttpServer.new
@rm = RouteMatcher.new
@server.request_handler(@rm)
@server.listen(8080)

@client = HttpClient.new
@client.port = 8080;

@params = { "name" => "foo", "version" => "v0.1"}
@re_params = { "param0" => "foo", "param1" => "v0.1"}
@regex = "\\/([^\\/]+)\\/([^\\/]+)"


def test_get_with_pattern
  route('get', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_get_with_regex
  route('get', true, @regex, @re_params, "/foo/v0.1")
end

def test_put_with_pattern
  route('put', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_put_with_regex
  route('put', true, @regex, @re_params, "/foo/v0.1")
end

def test_post_with_pattern
  route('post', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_post_with_regex
  route('post', true, @regex, @re_params, "/foo/v0.1")
end

def test_delete_with_pattern
  route('delete', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_delete_with_regex
  route('delete', true, @regex, @re_params, "/foo/v0.1")
end

def test_options_with_pattern
  route('options', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_options_with_regex
  route('options', true, @regex, @re_params, "/foo/v0.1")
end

def test_head_with_pattern
  route('head', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_head_with_regex
  route('head', true, @regex, @re_params, "/foo/v0.1")
end

def test_trace_with_pattern
  route('trace', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_trace_with_regex
  route('trace', true, @regex, @re_params, "/foo/v0.1")
end

def test_patch_with_pattern
  route('patch', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_patch_with_regex
  route('patch', true, @regex, @re_params, "/foo/v0.1")
end

def test_connect_with_pattern
  route('connect', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_connect_with_regex
  route('connect', true, @regex, @re_params, "/foo/v0.1")
end

def test_all_with_pattern
  route('all', false, "/:name/:version", @params, "/foo/v0.1")
end

def test_all_with_regex
  route('all', true, @regex, @re_params, "/foo/v0.1")
end

def test_route_no_match
  @client.get('some-uri') do |resp|
    @tu.azzert(404 == resp.status_code)
    @tu.test_complete
  end.end
end


def route(method, regex, pattern, params, uri)

  handler = Proc.new do |req|
    @tu.azzert(req.params.size == params.size)
    params.each do |k, v|
      @tu.azzert(v == req.params[k])
    end
    req.response.end
  end

  if regex
    @rm.send(method + '_re', pattern, handler)
  else
    @rm.send(method, pattern, handler)
  end

  method = 'get' if method == 'all'

  @client.send(method, uri) do |resp|
    @tu.azzert(200 == resp.status_code)
    @tu.test_complete
  end.end

end

def vertx_stop
  @tu.unregister_all
  @client.close
  @server.close do
    @tu.app_stopped
  end
end

@tu.register_all(self)
@tu.app_ready