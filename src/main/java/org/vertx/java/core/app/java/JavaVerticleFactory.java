package org.vertx.java.core.app.java;

import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.app.VerticleFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaVerticleFactory implements VerticleFactory {

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {

    Class clazz = cl.loadClass(main);

    Verticle verticle = (Verticle)clazz.newInstance();

    // Sanity check - make sure app class didn't get loaded by the parent or system classloader
    // This might happen if it's been put on the server classpath
    ClassLoader system = ClassLoader.getSystemClassLoader();
    ClassLoader appCL = clazz.getClassLoader();
    if (appCL == cl.getParent() || (system != null && appCL == system)) {
      throw new IllegalStateException("Do not add application classes to the vert.x classpath");
    }

    return verticle;

  }
}
