package org.vertx.java.core.app.jruby;

import org.vertx.java.core.app.VerticleFactory;
import org.vertx.java.core.app.Verticle;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class JRubyVerticleFactory implements VerticleFactory {
  public Verticle createVerticle(String main, ClassLoader cl) throws Exception {
    if (System.getProperty("jruby.home") == null) {
      throw new IllegalStateException("In order to deploy Ruby applications you must set JRUBY_HOME to point " +
          "at your JRuby installation");
    }
    Verticle app = new JRubyVerticle(main, cl);
    return app;
  }
}
