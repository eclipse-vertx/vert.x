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

vertx.createHttpServer().requestHandler { req ->

  println "Got request ${req.uri}"

  for (name in req.headers.names) {
    println "${name}: ${req.headers[name]}"
  }

  req.dataHandler { data -> println "Got data $data" }

  req.endHandler {
    // Now send back a response
    req.response.chunked = true

    10.times {
      req.response << "server-data-chunk-$it"
    }

    req.response.end()
  }
}.listen(8282)

