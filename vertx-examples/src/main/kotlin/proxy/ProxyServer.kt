/*
 * Copyright 2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.vertx.kotlin.examples.proxy

import org.vertx.java.deploy.Verticle

import org.vertx.kotlin.core.*

public class ProxyServer() : Verticle() {
    public override fun start() {
        val client = createHttpClient {
            setHost("www.microsoft.com")
            setPort(80)
        }

        createHttpServer {
            requestHandler {
                val req = this

                println("Proxying request: $uri")
                val forwardReq = client.request(method!!, uri!!) { resp ->
                    println("Proxying response: ${resp.statusCode}")
                    req.statusCode = resp.statusCode

                    req.responseHeaders.putAll(resp.headers())
                    req.setChunked(true)

                    resp.dataHandler { data ->
                        println("Proxying response body:" + data)
                        req.write(data)
                    }
                    resp.endHandler {
                        req.end()
                    }
                }

                forwardReq.headers()!!.putAll(req.headers())
                req.dataHandler { data ->
                    println("Proxying response body:" + data)
                    forwardReq.write(data)
                }
                req.endHandler {
                    forwardReq.end()
                }
            }
        }
        .listen(8080)
    }
}