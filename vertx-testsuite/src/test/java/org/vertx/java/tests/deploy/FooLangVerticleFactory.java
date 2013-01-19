package org.vertx.java.tests.deploy;

import org.vertx.java.core.json.JsonObject;
import org.vertx.java.deploy.Verticle;
import org.vertx.java.deploy.impl.ModuleClassLoader;
import org.vertx.java.deploy.impl.VerticleFactory;
import org.vertx.java.deploy.impl.VerticleManager;

public class FooLangVerticleFactory implements VerticleFactory {

  private VerticleManager manager;

  @SuppressWarnings("unused")
  private ModuleClassLoader mcl;

  @Override
  public void init(VerticleManager manager, ModuleClassLoader mcl) {
    this.manager = manager;
    this.mcl = mcl;
  }

  @Override
  public Verticle createVerticle(String main) throws Exception {

    return new Verticle() {

      @Override
      public void start() throws Exception {
        JsonObject config = manager.getConfig();
        String foo = config.getString("foo", "bar");
        if (foo.equalsIgnoreCase("bar")) {
          throw new Exception("foo must not be bar!");
        }
      }};
  }

  @Override
  public void reportException(Throwable t) {
    t.printStackTrace();
  }

}
