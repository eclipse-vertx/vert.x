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
  public void before() throws Exception {
    vertx = VertxFactory.newVertx();
  }

  @After
  public void after() throws Exception {
    vertx.stop();
    super.after();
  }
}
