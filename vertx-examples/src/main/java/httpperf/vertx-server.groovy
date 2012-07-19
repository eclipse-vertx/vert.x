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

vertx.createHttpServer().requestHandler { req ->
  //req.response.end()
  //req.response.sendFile("httpperf/foo.html")
  vertx.fileSystem.readFile("httpperf/foo.html") { ar ->
    req.response.headers["content-length"] = ar.result.length
    req.response.headers["content-type"] = "text/html"
    req.response.end(ar.result)
  }
}.listen(8080, 'localhost')

