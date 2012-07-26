package org.vertx.kotlin.framework

import org.vertx.java.deploy.Verticle
import org.vertx.java.framework.TestUtils

import org.vertx.kotlin.core.*

public abstract class TestVerticle() : Verticle() {
    private var _testUtils: TestUtils? = null

    // FIXME: kotlin compiler bug (no bridge when called from closure)
    public val testUtils: TestUtils
        get() {
            if (_testUtils == null) {
                _testUtils = TestUtils(vertx)
            }
            return _testUtils!!
        }

    public override final fun start() {
        doStart()

        logger.info("$this started")
        testUtils.registerTests(this)

        testUtils.appReady()
    }

    public override fun stop() {
        doStop ()

        logger.info("$this stopped")
        testUtils.unregisterAll()
        testUtils.appStopped()
    }

    protected open fun doStart () {}

    protected open fun doStop () {}
}