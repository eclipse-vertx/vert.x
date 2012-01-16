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

  handler = Proc.new do |req|
    @tu.azzert(req.params.size == params.size)
    params.each do |k, v|
      @tu.azzert(v == req.params[k])
    end
    req.response.end
  end

  if regex
    @rm.get_re(pattern, handler)
  else
    @rm.get(pattern, handler)
  end

  @client.get(uri) do |resp|
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