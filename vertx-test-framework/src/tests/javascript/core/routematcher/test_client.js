/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

load('test_utils.js')
load('vertx.js')

var tu = new TestUtils();


var server = vertx.createHttpServer();
var rm = new vertx.RouteMatcher();
server.requestHandler(rm);
server.listen(8080);

var client = vertx.createHttpClient().setPort(8080);

var params = { "name" : "foo", "version" : "v0.1"};
var re_params = { "param0" : "foo", "param1" :"v0.1"};
var regex = "\\/([^\\/]+)\\/([^\\/]+)";


function testGetWithPattern() {
  route('get', false, "/:name/:version", params, "/foo/v0.1")
}

function testGetWithRegEx() {
  route('get', true, regex, re_params, "/foo/v0.1");
}

function testPutWithPattern() {
  route('put', false, "/:name/:version", params, "/foo/v0.1");
}

function testPutWithRegEx() {
  route('put', true, regex, re_params, "/foo/v0.1");
}

function testPostWithPattern() {
  route('post', false, "/:name/:version", params, "/foo/v0.1");
}

function testPostWithRegEx() {
  route('post', true, regex, re_params, "/foo/v0.1");
}

function testDeleteWithPattern() {
  route('delete', false, "/:name/:version", params, "/foo/v0.1");
}

function testDeleteWithRegEx() {
  route('delete', true, regex, re_params, "/foo/v0.1");
}

function testOptionsWithPattern() {
  route('options', false, "/:name/:version", params, "/foo/v0.1");
}

function testOptionsWithRegEx() {
  route('options', true, regex, re_params, "/foo/v0.1");
}

function testHeadWithPattern() {
  route('head', false, "/:name/:version", params, "/foo/v0.1");
}

function testHeadWithRegEx() {
  route('head', true, regex, re_params, "/foo/v0.1");
}

function testTraceWithPattern() {
  route('trace', false, "/:name/:version", params, "/foo/v0.1");
}

function testTraceWithRegEx() {
  route('trace', true, regex, re_params, "/foo/v0.1");
}

function testPatchWithPattern() {
  route('patch', false, "/:name/:version", params, "/foo/v0.1");
}

function testPatchWithRegEx() {
  route('patch', true, regex, re_params, "/foo/v0.1");
}

function testConnectWithPattern() {
  route('connect', false, "/:name/:version", params, "/foo/v0.1");
}

function testConnectWithRegEx() {
  route('connect', true, regex, re_params, "/foo/v0.1");
}

function testAllWithPattern() {
  route('all', false, "/:name/:version", params, "/foo/v0.1");
}

function testAllWithRegEx() {
  route('all', true, regex, re_params, "/foo/v0.1");
}

function testRouteNoMatch() {
  client.get('some-uri', function(resp) {
    tu.azzert(404 === resp.statusCode);
    tu.testComplete();
  }).end();
}

function route(method, regex, pattern, params, uri) {

  var handler = function(req) {
    tu.azzert(req.params().length === params.length);
    for (k in req.params()) {
      tu.azzert(params[k] === req.params()[k]);
    }
    req.response.end();
  }

  if (regex) {
    rm[method + 'WithRegEx'](pattern, handler);
  } else {
    rm[method](pattern, handler);
  }

  if (method === 'all') {
    method = 'get';
  }

  client[method](uri, function(resp) {
    tu.azzert(200 == resp.statusCode)
    tu.testComplete();
  }).end();
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