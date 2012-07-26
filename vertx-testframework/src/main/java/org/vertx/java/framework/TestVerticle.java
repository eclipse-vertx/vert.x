package org.vertx.java.framework;

import org.vertx.java.deploy.Verticle;

public abstract class TestVerticle extends Verticle {
    protected TestUtils testUtils;

    public final void start() {
        testUtils = new TestUtils(vertx);
        doStart();

        getContainer().getLogger().info(this + " started");
        testUtils.registerTests(this);

        testUtils.appReady();
    }

    public void stop() {
        doStop ();

        getContainer().getLogger().info(this + " stopped");
        testUtils.unregisterAll();
        testUtils.appStopped();
    }

    protected void doStart () {}

    protected void doStop () {}
}
