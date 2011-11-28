package org.vertx.java.core.app.rhino;

import org.vertx.java.core.app.AppFactory;
import org.vertx.java.core.app.ParentLastURLClassLoader;
import org.vertx.java.core.app.VertxApp;
import org.vertx.java.core.app.rhino.RhinoApp;

import java.net.URL;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoAppFactory implements AppFactory {
  public VertxApp createApp(String main, URL[] urls, ClassLoader parentCL) throws Exception {
    ClassLoader cl = new ParentLastURLClassLoader(urls, getClass().getClassLoader());
    VertxApp app = new RhinoApp(main, cl);
    return app;
  }
}

