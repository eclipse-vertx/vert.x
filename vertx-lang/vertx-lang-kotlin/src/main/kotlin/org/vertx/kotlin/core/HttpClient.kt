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

import org.vertx.java.core.http.HttpClient
import org.vertx.java.core.http.HttpClientRequest
import org.vertx.java.core.http.HttpClientResponse
import org.vertx.java.core.http.WebSocket

public fun HttpClient.request(method: String, uri: String, handler: (HttpClientResponse)->Unit) : HttpClientRequest
        = this.request(method, uri, handler(handler))!!

public fun HttpClient.connectWebsocket(uri: String, handler: WebSocket.()->Any?) : Unit
        = this.connectWebsocket(uri,handler(handler))
