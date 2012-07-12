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

import vertx
from test_utils import TestUtils
from core.http import RouteMatcher

tu = TestUtils()

server = vertx.create_http_server()
rm = RouteMatcher()
server.request_handler(rm)
server.listen(8080)

client = vertx.create_http_client()
client.port = 8080;

class RouteMatcherTest(object):
    def __init__(self):
        self.params = { "name" : "foo", "version" : "v0.1"}
        self.re_params = { "param0" : "foo", "param1" : "v0.1"}
        self.regex = "\\/([^\\/]+)\\/([^\\/]+)"

    def test_get_with_pattern(self): 
        route('get', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_get_with_regex(self): 
        route('get', True, self.regex, self.re_params, "/foo/v0.1")


    def test_put_with_pattern(self): 
        route('put', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_put_with_regex(self): 
        route('put', True, self.regex, self.re_params, "/foo/v0.1")


    def test_post_with_pattern(self): 
        route('post', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_post_with_regex(self): 
        route('post', True, self.regex, self.re_params, "/foo/v0.1")


    def test_delete_with_pattern(self): 
        route('delete', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_delete_with_regex(self): 
        route('delete', True, self.regex, self.re_params, "/foo/v0.1")


    def test_options_with_pattern(self): 
        route('options', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_options_with_regex(self): 
        route('options', True, self.regex, self.re_params, "/foo/v0.1")


    def test_head_with_pattern(self): 
        route('head', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_head_with_regex(self): 
        route('head', True, self.regex, self.re_params, "/foo/v0.1")


    def test_trace_with_pattern(self): 
        route('trace', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_trace_with_regex(self): 
        route('trace', True, self.regex, self.re_params, "/foo/v0.1")


    def test_patch_with_pattern(self): 
        route('patch', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_patch_with_regex(self): 
        route('patch', True, self.regex, self.re_params, "/foo/v0.1")


    def test_connect_with_pattern(self): 
        route('connect', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_connect_with_regex(self): 
        route('connect', True, self.regex, self.re_params, "/foo/v0.1")


    def test_all_with_pattern(self): 
        route('all', False, "/:name/:version", self.params, "/foo/v0.1")


    def test_all_with_regex(self): 
        route('all', True, self.regex, self.re_params, "/foo/v0.1")


    def test_route_no_match(self):
        global client
        def response_handler(resp):
            tu.azzert(404 == resp.status_code)
            tu.test_complete()
        client.get('some-uri', response_handler).end()
        
def route(method, regex, pattern, params, uri): 
    def handler(req):
        tu.azzert(len(req.params) == len(params))
        for k,v in params.iteritems():
            tu.azzert(v == req.params[k])
        req.response.end()

    if regex:
        #print "calling rm.%s_re with pattern %s"%(method, pattern)
        getattr(rm, method + '_re')(pattern, handler)
    else:
        #print "calling rm.%s with pattern %s"%(method, pattern)
        getattr(rm, method)(pattern, handler)

    if method == 'all':
        method = 'get' 

    def response_handler(resp):
        tu.azzert(200 == resp.status_code)
        tu.test_complete()

    getattr(client, method)(uri, response_handler).end()

def vertx_stop(): 
    tu.unregister_all()
    client.close()

    @server.close
    def close():
        tu.app_stopped()

tu.register_all(RouteMatcherTest())
tu.app_ready()