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

import org.vertx.java.core.Handler

public fun <T> handler(handlerFun: (T)->Any?) : Handler<T?>  = object: Handler<T?> {
    public override fun handle(arg: T?) {
        handlerFun(arg!!)
    }
}

public fun <T> handler(handlerFun: T.()->Any?) : Handler<T?>  = object: Handler<T?> {
    public override fun handle(arg: T?) {
        arg!!.handlerFun()
    }
}

public fun handler(handlerFun: ()->Any?) : Handler<Void?>  = object: Handler<Void?> {
    public override fun handle(_: Void?) {
        handlerFun()
    }
}
