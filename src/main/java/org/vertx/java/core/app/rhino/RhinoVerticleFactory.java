package org.vertx.java.core.app.rhino;

import org.mozilla.javascript.ContextFactory;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.app.VerticleFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class RhinoVerticleFactory implements VerticleFactory {

  static {
    ContextFactory.initGlobal(new RhinoContextFactory());
  }

  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {
    Verticle app = new RhinoVerticle(main, cl);
    return app;
  }
}

