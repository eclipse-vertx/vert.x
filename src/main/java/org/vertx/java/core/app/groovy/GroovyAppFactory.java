package org.vertx.java.core.app.groovy;

import groovy.lang.GroovyClassLoader;
import org.vertx.java.core.app.AppFactory;
import org.vertx.java.core.app.ParentLastURLClassLoader;
import org.vertx.java.core.app.VertxApp;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class GroovyAppFactory implements AppFactory {

  public VertxApp createApp(String main, URL[] urls, ClassLoader parentCL) throws Exception {
    ClassLoader cl = new ParentLastURLClassLoader(urls, parentCL);

    InputStream is = cl.getResourceAsStream(main);
    GroovyClassLoader gcl = new GroovyClassLoader(cl);
    Class clazz = gcl.parseClass(is);
    try {
      is.close();
    } catch (IOException ignore) {
    }
    VertxApp app = (VertxApp)clazz.newInstance();

    return app;
  }
}

