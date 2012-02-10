load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();


var server = new vertx.HttpServer();
var rm = new vertx.RouteMatcher();
server.requestHandler(rm);
server.listen(8080);

var client = new vertx.HttpClient().setPort(8080);

var params = { "name" : "foo", "version" : "v0.1"};
var re_params = { "param0" : "foo", "param1" :"v0.1"};
var regex = "\\/([^\\/]+)\\/([^\\/]+)";


function testGetWithPattern() {
  log.println("in test get with pattern");
  route('get', false, "/:name/:version", params, "/foo/v0.1")
}

//def test_get_with_regex
//  route('get', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_put_with_pattern
//  route('put', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_put_with_regex
//  route('put', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_post_with_pattern
//  route('post', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_post_with_regex
//  route('post', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_delete_with_pattern
//  route('delete', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_delete_with_regex
//  route('delete', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_options_with_pattern
//  route('options', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_options_with_regex
//  route('options', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_head_with_pattern
//  route('head', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_head_with_regex
//  route('head', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_trace_with_pattern
//  route('trace', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_trace_with_regex
//  route('trace', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_patch_with_pattern
//  route('patch', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_patch_with_regex
//  route('patch', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_connect_with_pattern
//  route('connect', false, "/:name/:version", @params, "/foo/v0.1")
//end
//
//def test_connect_with_regex
//  route('connect', true, @regex, @re_params, "/foo/v0.1")
//end
//
//def test_route_no_match
//  @client.send('get', 'some-uri') do |resp|
//    @tu.azzert(404 == resp.status_code)
//    @tu.test_complete
//  end.end
//end


function route(method, regex, pattern, params, uri) {

  var handler = function(req) {
    log.println("in handler");
    log.println("req.params is " + req.params());
    tu.azzert(req.params().length === params.length);
    for (k in req.params()) {
      tu.azzert(params[k] === req.pparams()[k]);
    }
    req.response.end();
  }

  if (regex) {
    rm[method + '_re'](pattern, handler);
  } else {
    rm[method](pattern, handler);
  }

  client[method](uri, function(resp) {
    tu.azzert(200 == resp.statusCode)
    tu.testComplete();
  }).end();
  log.println("Sent request");
}

tu.registerTests(this);
tu.appReady();

function vertxStop() {
  tu.unregisterAll();
  tu.appStopped();
  server.close(function() {
    client.close();
  })
}