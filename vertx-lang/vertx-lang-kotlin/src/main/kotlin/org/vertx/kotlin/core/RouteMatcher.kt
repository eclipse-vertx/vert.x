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

import org.vertx.java.core.http.RouteMatcher
import org.vertx.java.core.http.HttpServerRequest
import org.vertx.java.core.http.HttpServer

public fun RouteMatcher(config: RouteMatcher.() -> Any?) : RouteMatcher {
    val matcher = RouteMatcher()
    matcher.config()
    return matcher
}

public fun RouteMatcher.get(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = get(pattern, handler(handler))

public fun RouteMatcher.put(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = put(pattern, handler(handler))

public fun RouteMatcher.post(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = post(pattern, handler(handler))

public fun RouteMatcher.delete(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = delete(pattern, handler(handler))

public fun RouteMatcher.options(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = options(pattern, handler(handler))

public fun RouteMatcher.head(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = head(pattern, handler(handler))

public fun RouteMatcher.trace(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = trace(pattern, handler(handler))

public fun RouteMatcher.connect(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = connect(pattern, handler(handler))

public fun RouteMatcher.patch(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = patch(pattern, handler(handler))

public fun RouteMatcher.all(pattern: String, handler: HttpServerRequest.() -> Any?): Unit
        = all(pattern, handler(handler))

public fun RouteMatcher.getWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = getWithRegEx(regex, handler(handler))

public fun RouteMatcher.putWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = putWithRegEx(regex, handler(handler))

public fun RouteMatcher.postWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = postWithRegEx(regex, handler(handler))

public fun RouteMatcher.deleteWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = deleteWithRegEx(regex, handler(handler))

public fun RouteMatcher.optionsWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = optionsWithRegEx(regex, handler(handler))

public fun RouteMatcher.headWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = headWithRegEx(regex, handler(handler))

public fun RouteMatcher.traceWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = traceWithRegEx(regex, handler(handler))

public fun RouteMatcher.connectWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = connectWithRegEx(regex, handler(handler))

public fun RouteMatcher.patchWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = patchWithRegEx(regex, handler(handler))

public fun RouteMatcher.allWithRegEx(regex: String, handler: HttpServerRequest.() -> Any?): Unit
        = allWithRegEx(regex, handler(handler))

public fun RouteMatcher.noMatch(handler: HttpServerRequest.() -> Any?): Unit
        = noMatch(handler(handler))
