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
package proxy

def client = vertx.createHttpClient(port: 8282)

def server = vertx.createHttpServer().requestHandler { req ->
  println "Proxying request: ${req.uri}"

  def c_req = client.request(req.method, req.uri) { c_res ->
    println "Proxying response: ${c_res.statusCode}"
    req.response.chunked = true
    req.response.statusCode = c_res.statusCode
    req.response.headers << c_res.headers
    c_res.dataHandler { data ->
      println "Proxying response body: $data"
      req.response << data
    }
    c_res.endHandler { req.response.end() }
  }
  c_req.chunked = true
  c_req.headers << req.headers
  req.dataHandler { data ->
    println "Proxying request body ${data}"
    c_req << data
  }
  req.endHandler{ c_req.end() }

}.listen(8080)
