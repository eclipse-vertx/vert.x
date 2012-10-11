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

import org.vertx.java.core.net.NetServer
import org.vertx.java.core.net.NetSocket

public fun NetServer?.connectHandler(handler: (NetSocket)->Unit): NetServer {
    this!!.connectHandler(handler(handler))
    return this
}

public fun NetServer.close(handler: ()->Unit) {
    close(handler(handler))
}

public var NetServer.SSL : Boolean
    set(v: Boolean) { setSSL(v) }
    get() = this.isSSL()

public var NetServer.keyStorePath : String
        set(v: String) { setKeyStorePath(v) }
        get() = getKeyStorePath()!!

public var NetServer.keyStorePassword : String
    set(v: String) { setKeyStorePassword(v) }
    get() = getKeyStorePassword()!!

public var NetServer.trustStorePath : String
    set(v: String) { setTrustStorePath(v) }
    get() = getTrustStorePath()!!

public var NetServer.trustStorePassword : String
    set(v: String) { setTrustStorePassword(v) }
    get() = getTrustStorePassword()!!

public var NetServer.clientAuthRequired : Boolean
    get() = throw UnsupportedOperationException()
    set(v: Boolean) { setClientAuthRequired(v) }

public var NetServer.tcpNoDelay : Boolean
    get() = isTCPNoDelay()!!
    set(v: Boolean) { setTCPNoDelay(v) }

public var NetServer.tcpKeepAlive : Boolean
    get() = isTCPKeepAlive()!!
    set(v: Boolean) { setTCPKeepAlive(v) }

public var NetServer.reuseAddress : Boolean
    get() = isReuseAddress()!!
    set(v: Boolean) { setReuseAddress(v) }

public var NetServer.soLinger : Boolean
    get() = isSoLinger()!!
    set(v: Boolean) { setSoLinger(v) }

public var NetServer.sendBufferSize : Int
    get() = getSendBufferSize()!!
    set(v: Int) { setSendBufferSize(v) }

public var NetServer.receiveBufferSize : Int
    get() = getReceiveBufferSize()!!
    set(v: Int) { setReceiveBufferSize(v) }

public var NetServer.trafficClass : Int
    get() = getTrafficClass()!!
    set(v: Int) { setTrafficClass(v) }

public var NetServer.acceptBacklog : Int
    get() = getAcceptBacklog()!!
    set(v: Int) { setAcceptBacklog(v) }
