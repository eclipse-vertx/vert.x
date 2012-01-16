package org.vertx.java.core.app.rhino;

import org.vertx.java.core.app.AppFactory;
import org.vertx.java.core.app.VertxApp;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoAppFactory implements AppFactory {
  public VertxApp createApp(String main, ClassLoader cl) throws Exception {
    VertxApp app = new RhinoApp(main, cl);
    return app;
  }
}

