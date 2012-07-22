package org.vertx.kotlin.core

import org.vertx.java.core.json.JsonObject
import org.vertx.java.core.json.JsonArray
import org.vertx.java.deploy.Verticle

public trait ConfigurableVerticle : Verticle {
    public override fun start() {
        logger.info("Starting ${getClass()!!.getName()} Configuration:\n$config")
        loadConfiguredVerticles()
    }

    protected fun loadConfiguredVerticle(config: JsonObject) {
        val instancesConfigured = config.getNumber("verticle_instances")
        val instances = if(instancesConfigured != null) instancesConfigured.toInt() else 1

        val main = config.getString("verticle_main")!!
        logger.info("Deploying $main. $instances instance(s) Configuration:\n$config")
        deployVerticle(
                main,
                config,
                instances
        )
    }

    protected fun loadConfiguredVerticles() {
        val config = this.config
        val verticles = config.getField("nested_verticles")
        if(verticles != null)
            when(verticles) {
                is JsonArray ->
                    for(val verticle in verticles as JsonArray) {
                        loadConfiguredVerticle(verticle as JsonObject)
                    }
                is JsonObject ->
                    loadConfiguredVerticle(verticles as JsonObject)
                else ->
                    throw IllegalStateException("'nested_verticles' must be either array of objects or an object")
            }
    }
}
