package org.vertx.kotlin.deploy

import org.vertx.java.deploy.impl.VerticleManager
import org.vertx.java.deploy.VerticleFactory
import org.vertx.java.deploy.Verticle
import org.jetbrains.jet.cli.jvm.K2JVMCompiler
import org.jetbrains.jet.cli.jvm.K2JVMCompilerArguments
import java.util.Collections
import java.net.URLClassLoader
import java.io.File
import org.jetbrains.jet.internal.com.intellij.openapi.util.Disposer
import java.io.PrintStream
import org.jetbrains.jet.cli.common.messages.MessageRenderer
import org.jetbrains.jet.cli.common.messages.PrintingMessageCollector
import org.jetbrains.jet.cli.jvm.compiler.CompileEnvironmentUtil
import org.jetbrains.jet.config.CompilerConfiguration
import org.jetbrains.jet.cli.jvm.JVMConfigurationKeys
import java.util.LinkedList
import org.jetbrains.jet.config.CommonConfigurationKeys
import org.jetbrains.jet.cli.jvm.compiler.JetCoreEnvironment
import org.jetbrains.jet.cli.jvm.compiler.K2JVMCompileEnvironmentConfiguration
import org.jetbrains.jet.lang.BuiltinsScopeExtensionMode
import org.jetbrains.jet.codegen.BuiltinToJavaTypesMapping
import org.jetbrains.jet.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.jet.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.jet.cli.common.messages.MessageUtil
import org.jetbrains.jet.cli.common.messages.CompilerMessageLocation
import org.jetbrains.jet.codegen.CompilationException
import org.jetbrains.jet.lang.resolve.AnalyzerScriptParameter
import org.jetbrains.jet.lang.resolve.name.Name
import org.jetbrains.jet.lang.types.ref.JetTypeName
import org.vertx.java.core.Vertx
import org.vertx.java.deploy.Container
import java.util.Arrays

public class KotlinVerticleFactory() : VerticleFactory {
    private var mgr : VerticleManager? = null

    public override fun init(manager : VerticleManager?) : Unit {
        this.mgr = mgr
    }

    public override fun getLanguage() : String? {
        return "kotlin"
    }

    public override fun isFactoryFor(main : String?)  = main!!.endsWith(".ktscript")

    public override fun createVerticle(main : String?, parentCL : ClassLoader?) : Verticle? {
        val verxtParameter = AnalyzerScriptParameter(Name.identifier("vertx"), JetTypeName.fromJavaClass(javaClass<Vertx>()))
        val containerParameter = AnalyzerScriptParameter(Name.identifier("container"), JetTypeName.fromJavaClass(javaClass<Container>()))
        val verticleParameter = AnalyzerScriptParameter(Name.identifier("verticle"), JetTypeName.fromJavaClass(javaClass<KotlinScriptVerticle>()))
        val script = KotlinToJVMBytecodeCompiler.compileScript(parentCL, main, Arrays.asList(verxtParameter, containerParameter, verticleParameter))!!
        return KotlinScriptVerticle(script)
    }

    public override fun reportException(t : Throwable?) : Unit {
        t!!.printStackTrace()
        mgr?.getLogger()!!.error("Exception in Kotlin verticle script", t)
    }
}