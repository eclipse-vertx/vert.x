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

import org.vertx.java.core.net.NetClient
import org.vertx.java.core.net.NetSocket

public fun NetClient?.connect(port: Int,  connectCallback: (NetSocket)->Unit): NetClient
        = this!!.connect(port, handler(connectCallback))!!

public fun NetClient?.connect(port: Int,  host: String, connectCallback: (NetSocket)->Unit): NetClient
        = this!!.connect(port, host, handler(connectCallback))!!

public var NetClient.SSL : Boolean
    set(v: Boolean) { setSSL(v) }
    get() = this.isSSL()

public var NetClient.keyStorePath : String
    set(v: String) { setKeyStorePath(v) }
    get() = getKeyStorePath()!!

public var NetClient.keyStorePassword : String
    set(v: String) { setKeyStorePassword(v) }
    get() = getKeyStorePassword()!!

public var NetClient.trustStorePath : String
    set(v: String) { setTrustStorePath(v) }
    get() = getTrustStorePath()!!

public var NetClient.trustStorePassword : String
    set(v: String) { setTrustStorePassword(v) }
    get() = getTrustStorePassword()!!

public var NetClient.tcpNoDelay : Boolean
    get() = isTCPNoDelay()!!
    set(v: Boolean) { setTCPNoDelay(v) }

public var NetClient.tcpKeepAlive : Boolean
    get() = isTCPKeepAlive()!!
    set(v: Boolean) { setTCPKeepAlive(v) }

public var NetClient.reuseAddress : Boolean
    get() = isReuseAddress()!!
    set(v: Boolean) { setReuseAddress(v) }

public var NetClient.soLinger : Boolean
    get() = isSoLinger()!!
    set(v: Boolean) { setSoLinger(v) }

public var NetClient.sendBufferSize : Int
    get() = getSendBufferSize()!!
    set(v: Int) { setSendBufferSize(v) }

public var NetClient.receiveBufferSize : Int
    get() = getReceiveBufferSize()!!
    set(v: Int) { setReceiveBufferSize(v) }

public var NetClient.trafficClass : Int
    get() = getTrafficClass()!!
    set(v: Int) { setTrafficClass(v) }
