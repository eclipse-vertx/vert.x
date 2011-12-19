package org.vertx.java.core.app.java;

import org.vertx.java.core.app.AppFactory;
import org.vertx.java.core.app.ParentLastURLClassLoader;
import org.vertx.java.core.app.VertxApp;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JavaAppFactory implements AppFactory {

  public VertxApp createApp(String main, ClassLoader cl) throws Exception {

    Class clazz = cl.loadClass(main);

    VertxApp app = (VertxApp)clazz.newInstance();

    // Sanity check - make sure app class didn't get loaded by the parent or system classloader
    // This might happen if it's been put on the server classpath
    ClassLoader system = ClassLoader.getSystemClassLoader();
    ClassLoader appCL = clazz.getClassLoader();
    if (appCL == cl.getParent() || (system != null && appCL == system)) {
      throw new IllegalStateException("Do not add application classes to the vert.x classpath");
    }

    return app;

  }
}
