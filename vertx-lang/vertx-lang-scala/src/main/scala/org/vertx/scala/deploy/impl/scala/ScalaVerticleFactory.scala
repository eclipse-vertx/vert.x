package org.vertx.scala.deploy.impl.scala

import tools.nsc.interpreter.{IMain, AbstractFileClassLoader}
import org.vertx.java.deploy.impl.VerticleManager
import org.vertx.java.deploy.{Verticle, VerticleFactory}
import tools.nsc.Settings
import tools.nsc.util.BatchSourceFile
import tools.nsc.io.PlainFile

/**
 * @author Lucien Pereira
 */
class ScalaVerticleFactory extends VerticleFactory {

  var verticleManager: Option[VerticleManager] = None

  def init(verticleManager: VerticleManager): Unit = {
    this.verticleManager = Option(verticleManager)
  }

  def createVerticle(filepath: String, classLoader: ClassLoader): Verticle = {
    val abstractFileClassLoader: AbstractFileClassLoader = compile(filepath)
    val fqn = filepath.substring(filepath.lastIndexOf('/') + 1, filepath.lastIndexOf('.'))
    abstractFileClassLoader.loadClass(fqn).newInstance().asInstanceOf[Verticle]
  }

  def reportException(throwable: Throwable): Unit = {
    verticleManager.map {
      vm =>
        vm.getLogger.error(throwable.getMessage, throwable)
    }
  }

  private def compile(filePath: String): AbstractFileClassLoader = {
    val settings = new Settings()
    settings.usejavacp.value = true
    val interpreter = new IMain(settings)

    interpreter.compileSources(new BatchSourceFile(PlainFile.fromPath(filePath)))
    interpreter.classLoader
  }
}