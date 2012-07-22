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
package org.vertx.kotlin.core

import org.vertx.java.core.http.HttpServer
import org.vertx.java.core.http.HttpServerRequest
import org.vertx.java.core.http.ServerWebSocket
import org.vertx.java.core.http.RouteMatcher
import org.vertx.java.core.Handler
import org.vertx.java.core.Vertx

public fun HttpServer.requestHandler(handlerFun: HttpServerRequest.()->Any?): HttpServer
        = requestHandler(handler(handlerFun))!!

public fun HttpServer.websocketHandler(handlerFun: (ServerWebSocket)->Any?): HttpServer
        = websocketHandler(handler(handlerFun))!!

public fun HttpServer.routeMatcher(config: RouteMatcher.()->Unit): HttpServer
        = requestHandler(RouteMatcher(config) as Handler<HttpServerRequest?>)!!

public var HttpServer.sendBufferSize : Int
    get() = getSendBufferSize()!!
    set(v: Int) {
        setSendBufferSize(v)
    }

public var HttpServer.receiveBufferSize : Int
    get() = getReceiveBufferSize()!!
    set(v: Int) {
        setReceiveBufferSize(v)
    }

public var HttpServer.acceptBacklog : Int
    get() = getAcceptBacklog()!!
    set(v: Int) {
        setAcceptBacklog(v)
    }
