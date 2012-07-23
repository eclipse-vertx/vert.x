/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.vertx.kotlin.examples.echo

import org.vertx.java.deploy.Verticle
import org.vertx.java.core.streams.Pump

import org.vertx.kotlin.core.*
import org.vertx.java.core.buffer.Buffer

public class EchoSslClient() : Verticle() {
    public override fun start() {
        createNetClient {
            setSSL(true)
            setTrustAll(true)

            connect(1234){ socket ->
                socket.dataHandler{ buffer ->
                    System.out.println("Net client receiving:\n-------\n$buffer\n-------")
                }

                //Now send some data
                for (var i in 0..9) {
                    val str = "hello$i\n"
                    System.out.print("Net client sending: $str")
                    socket.write(str)
                }
            }
        }
    }
}