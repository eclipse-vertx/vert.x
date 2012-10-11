package org.vertx.kotlin.deploy

import java.util.Arrays
import org.jetbrains.jet.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.jet.lang.resolve.AnalyzerScriptParameter
import org.jetbrains.jet.lang.resolve.name.Name
import org.jetbrains.jet.lang.types.ref.JetTypeName
import org.vertx.java.core.Vertx
import org.vertx.java.deploy.Container
import org.vertx.java.deploy.Verticle
import org.vertx.java.deploy.VerticleFactory
import org.vertx.java.deploy.impl.VerticleManager

public class KotlinVerticleFactory() : VerticleFactory {
    private var mgr : VerticleManager? = null

    public override fun init(manager : VerticleManager?) : Unit {
        this.mgr = mgr
    }

    public override fun createVerticle(main : String?, parentCL : ClassLoader?) : Verticle? {
        val verxtParameter = AnalyzerScriptParameter(Name.identifier("vertx"), JetTypeName.fromJavaClass(javaClass<Vertx>()))
        val containerParameter = AnalyzerScriptParameter(Name.identifier("container"), JetTypeName.fromJavaClass(javaClass<Container>()))
        val verticleParameter = AnalyzerScriptParameter(Name.identifier("verticle"), JetTypeName.fromJavaClass(javaClass<KotlinScriptVerticle>()))
        val script = KotlinToJVMBytecodeCompiler.compileScript(parentCL, main, Arrays.asList(verxtParameter, containerParameter, verticleParameter), null)!!
        return KotlinScriptVerticle(script)
    }

    public override fun reportException(t : Throwable?) : Unit {
        t!!.printStackTrace()
        mgr?.getLogger()!!.error("Exception in Kotlin verticle script", t)
    }
}