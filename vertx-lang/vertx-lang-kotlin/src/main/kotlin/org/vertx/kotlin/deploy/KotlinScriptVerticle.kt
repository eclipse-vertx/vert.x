package org.vertx.kotlin.deploy

import org.vertx.java.deploy.Verticle

public class KotlinScriptVerticle(val clazz: Class<*>) : Verticle() {
    public var onStart: (()-> Any?)? = null
    public var onStop:  (()-> Any?)? = null

    public override fun start() {
        clazz.getConstructors()!![0]!!.newInstance(vertx, container, this)
        if(onStart != null) {
            onStart!!()
        }
    }

    public override fun stop() {
        if(onStop != null) {
            onStop!!()
        }
    }
}