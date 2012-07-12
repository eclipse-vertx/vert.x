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
load('vertx.js')

var client = vertx.createHttpClient().setPort(8282);

vertx.createHttpServer().requestHandler(function(req) {
  stdout.println("Proxying request: " + req.uri);

  var c_req = client.request(req.method, req.uri, function(c_res) {
    stdout.println("Proxying response: " + c_res.statusCode);
    req.response.statusCode = c_res.statusCode;
    req.response.setChunked(true);
    req.response.putAllHeaders(c_res.headers());
    c_res.dataHandler(function(data) {
      stdout.println("Proxying response body: " + data);
      req.response.writeBuffer(data);
    });
    c_res.endHandler(function() { req.response.end() });
  });
  c_req.setChunked(true);
  c_req.putAllHeaders(req.headers());
  req.dataHandler(function(data) {
    stdout.println("Proxying request body " + data);
    c_req.writeBuffer(data);
  });
  req.endHandler(function() { c_req.end() });

}).listen(8080)