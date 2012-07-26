package org.vertx.kotlin.deploy

import org.vertx.java.deploy.impl.VerticleManager
import org.vertx.java.deploy.VerticleFactory
import org.vertx.java.deploy.Verticle

public class KotlinVerticleFactory() : VerticleFactory {
    private var mgr : VerticleManager? = null

    public override fun init(manager : VerticleManager?) : Unit {
        this.mgr = mgr
    }

    public override fun getLanguage() : String? {
        return "kotlin"
    }

    public override fun isFactoryFor(main : String?)  = main!!.endsWith(".ktscript") || main!!.endsWith(".kt")

    public override fun createVerticle(main : String?, parentCL : ClassLoader?) : Verticle? {
        return null
    }

    public override fun reportException(t : Throwable?) : Unit {
        mgr?.getLogger()!!.error("Exception in Kotlin verticle script", t)
    }
}

val dummy = 0