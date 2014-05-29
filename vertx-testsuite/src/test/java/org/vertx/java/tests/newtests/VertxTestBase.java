package org.vertx.java.tests.newtests;

import org.junit.After;
import org.junit.Before;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxTestBase extends AsyncTestBase {

  protected Vertx vertx;

  @Before
  public void beforeVertxTestBase() throws Exception {
    vertx = VertxFactory.newVertx();
  }

  @After
  public void afterVertxTestBase() throws Exception {
    vertx.stop();
  }
}
