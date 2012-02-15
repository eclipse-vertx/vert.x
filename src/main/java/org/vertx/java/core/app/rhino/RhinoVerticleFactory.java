package org.vertx.java.core.app.rhino;

import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;
import org.vertx.java.core.app.Verticle;
import org.vertx.java.core.app.VerticleFactory;
import org.vertx.java.core.app.VerticleManager;
import org.vertx.java.core.logging.Logger;

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

  public void reportException(Throwable t) {

    Logger logger = VerticleManager.instance.getLogger();

    if (t instanceof RhinoException) {
      RhinoException je = (RhinoException)t;

      logger.error("Exception in JavaScript verticle:\n"
                   + je.details() +
                   "\n" + je.getScriptStackTrace());
    } else {
      logger.error("Exception in JavaScript verticle", t);
    }
  }
}

