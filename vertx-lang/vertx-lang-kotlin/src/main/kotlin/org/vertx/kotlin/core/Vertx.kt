/*
 * Copyright 2012 the original author or authors.
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

package org.vertx.kotlin.core

import org.vertx.java.core.Vertx
import org.vertx.java.core.http.HttpServer
import org.vertx.java.core.http.HttpClient
import org.vertx.java.core.net.NetServer
import org.vertx.java.core.net.NetClient
import org.vertx.java.core.shareddata.SharedData

public fun Vertx?.createHttpServer(config: HttpServer.()->Unit) : HttpServer {
    val httpServer = this!!.createHttpServer()!!
    httpServer.config()
    return httpServer
}

public fun Vertx?.createHttpClient(config: HttpClient.()->Unit) : HttpClient {
    val httpClient = this!!.createHttpClient()!!
    httpClient.config()
    return httpClient
}

public fun Vertx?.createNetServer(config: NetServer.()->Unit) : NetServer {
    val netServer = this!!.createNetServer()!!
    netServer.config()
    return netServer
}

public fun Vertx?.createNetClient(config: NetClient.()->Unit) : NetClient {
    val netClient = this!!.createNetClient()!!
    netClient.config()
    return netClient
}

public fun Vertx?.setPeriodic(l: Long, longHandler: (Long)->Unit) : Long
        = this!!.setPeriodic(l, handler(longHandler))

public fun Vertx?.runOnLoop(handler: ()->Any?) : Unit = this!!.runOnLoop(handler(handler))

public val Vertx.sharedData : SharedData
    get() = sharedData()!!
