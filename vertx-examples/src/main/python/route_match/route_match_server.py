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

import vertx
from core.http import RouteMatcher

# Inspired from Sinatra / Express
rm = RouteMatcher()

def user_request_handler(req):
    req.response.end("User: %s ID: %s"% (req.params["user"],  req.params["id"]) ) 

# Extract the params from the uri
rm.get('/details/:user/:id', user_request_handler)

def request_handler(req):
    req.response.send_file("route_match/index.html")

# Catch all - serve the index page
rm.get_re('.*', request_handler)

vertx.create_http_server().request_handler(rm).listen(8080)