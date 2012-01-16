package org.vertx.java.core.app.groovy;

import groovy.lang.GroovyClassLoader;
import org.vertx.java.core.app.AppFactory;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class GroovyAppFactory implements AppFactory {

  private static final Logger log = Logger.getLogger(GroovyAppFactory.class);

  public VertxApp createApp(String main, ClassLoader cl) throws Exception {

    InputStream is = cl.getResourceAsStream(main);
    GroovyClassLoader gcl = new GroovyClassLoader(cl);
    Class clazz = gcl.parseClass(is);
    try {
      is.close();
    } catch (IOException ignore) {
    }

    Method stop;
    try {
      stop = clazz.getMethod("vertxStop", (Class<?>[])null);
    } catch (NoSuchMethodException e) {
      stop = null;
    }
    final Method mstop = stop;

    Method run;
    try {
      run = clazz.getMethod("run", (Class<?>[])null);
    } catch (NoSuchMethodException e) {
      run = null;
    }
    final Method mrun = run;

    if (run == null) {
      throw new IllegalStateException("Groovy script must have run() method [whether implicit or not]");
    }

    final Object app = clazz.newInstance();

    return new VertxApp() {
      public void start() {
        try {
            mrun.invoke(app, (Object[])null);
          } catch (Exception e) {
            log.error("Failed to run Groovy application", e);
          }
      }

      public void stop() {
        if (mstop != null) {
          try {
            mstop.invoke(app, (Object[])null);
          } catch (Exception e) {
            log.error("Failed to stop Groovy application", e);
          }
        }
      }
    };
  }
}

