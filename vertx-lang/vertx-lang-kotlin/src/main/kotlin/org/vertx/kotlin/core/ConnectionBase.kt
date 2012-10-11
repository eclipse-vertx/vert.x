package org.vertx.kotlin.core

import org.vertx.java.core.net.impl.ConnectionBase

public fun ConnectionBase.closedHandler(handler: ()->Any?) {
    this.closedHandler (handler(handler))
}