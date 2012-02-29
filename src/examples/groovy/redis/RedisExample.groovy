/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package redis

import org.vertx.groovy.core.http.HttpServer
import org.vertx.java.old.redis.RedisPool
import org.vertx.java.core.buffer.Buffer
import org.vertx.java.core.impl.CompletionHandler

final pool = new RedisPool()
final key = Buffer.create("my_count")

server = new HttpServer().requestHandler { req ->
  final redisConn = pool.connection()
  if (req.uri.equals("/")) {
    redisConn.incr(key).handler({ future ->
      def content = Buffer.create("<html><payload><h1>Hit count is ${future.result()}</h1></payload></html>")
      req.response["Content-Type"] = "text/html; charset=UTF-8"
      req.response["Content-Length"] = content.length().toString()
      req.response << content
      req.response.end()
      redisConn.close();
    } as CompletionHandler).execute()
  } else {
    req.response.statusCode = 404
    req.response.end()
  }
}.listen(8080)


void vertxStop() {
  server.close()
}

