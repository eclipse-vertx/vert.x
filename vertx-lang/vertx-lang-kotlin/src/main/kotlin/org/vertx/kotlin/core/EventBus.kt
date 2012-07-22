package org.vertx.kotlin.core

import org.vertx.java.core.eventbus.EventBus
import org.vertx.java.core.eventbus.Message
import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.Handler

public fun <T> EventBus.registerLocalHandler(where: String? = null, localHandler: Message<T>.()->Any?) : String
    = (if(where != null)
        registerLocalHandler(where, handler<Message<T>>(localHandler))
       else
        registerLocalHandler(handler<Message<T>>(localHandler))
    )!!

public fun EventBus.post(where: String, msg: JsonObject, replyHandler: (Message<JsonObject?>)->Any?) : Unit
        = this.send(where, msg, handler(replyHandler))
