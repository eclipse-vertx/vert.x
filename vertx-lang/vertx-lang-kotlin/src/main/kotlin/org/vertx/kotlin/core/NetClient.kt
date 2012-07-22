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
