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

import org.vertx.java.core.http.HttpServerRequest
import java.util.Map
import org.vertx.java.core.buffer.Buffer
import org.w3c.dom.Node
import kotlin.dom.toXmlString

public var HttpServerRequest.statusCode: Int
    get() = response!!.statusCode
    set(v : Int) {
        response!!.statusCode = v
    }

public var HttpServerRequest.statusMessage: String
    get() = response!!.statusMessage!!
    set(v : String) {
        response!!.statusMessage = v
    }

public fun HttpServerRequest.setChunked(b: Boolean) : Unit {
    response!!.setChunked(b)
}

public val HttpServerRequest.requestParams : Map<String?,String?>
    get() = params()!!

public val HttpServerRequest.responseHeaders : Map<String?,Any?>
    get() = response!!.headers()!!

public val HttpServerRequest.requestHeaders : Map<String?,String?>
    get() = headers()!!

public fun HttpServerRequest.putHeader(s: String, o: Any) : HttpServerRequest {
    response!!.putHeader(s, o)
    return this
}

public val HttpServerRequest.trailers : Map<String,Any?>
    get() = response!!.trailers() as Map<String,Any?>

public fun HttpServerRequest.putTrailer(s: String, o: Any) : HttpServerRequest {
    response!!.putTrailer(s, o)
    return this
}

public fun HttpServerRequest.closeHandler(voidHandler: ()->Unit) : HttpServerRequest {
    response!!.closeHandler(handler(voidHandler))
    return this
}

public fun HttpServerRequest.write(buffer: Buffer) : HttpServerRequest {
    response!!.write(buffer)
    return this
}

public fun HttpServerRequest.write(s: String, s1: String) : HttpServerRequest {
    response!!.write(s, s1)
    return this
}

public fun HttpServerRequest.write(s: String) : HttpServerRequest {
    response!!.write(s)
    return this
}

public fun HttpServerRequest.write(buffer: Buffer, voidHandler: ()-> Unit) : HttpServerRequest {
    response!!.write(buffer, handler(voidHandler))
    return this
}

public fun HttpServerRequest.write(s: String, s1: String, voidHandler: () -> Unit) : HttpServerRequest {
    response!!.write(s, s1, handler(voidHandler))
    return this
}

public fun HttpServerRequest.write(s: String, voidHandler: ()-> Unit) : HttpServerRequest {
    response!!.write(s, handler(voidHandler))
    return this
}

public fun HttpServerRequest.end(s: String) : Unit = response!!.end(s)

public fun HttpServerRequest.end(s: String, s1: String) : Unit = response!!.end(s, s1)

public fun HttpServerRequest.end(buffer: Buffer) : Unit = response!!.end(buffer)

public fun HttpServerRequest.end(node: Node) : Unit = response!!.end(node.toXmlString())

public fun HttpServerRequest.end(): Unit = response!!.end()

public fun HttpServerRequest. sendFile(file: String) : HttpServerRequest {
    response!!.sendFile(file)
    return this
}

public fun HttpServerRequest.close() : Unit {
    response!!.close()
}
