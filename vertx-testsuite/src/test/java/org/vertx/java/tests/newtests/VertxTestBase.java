package org.vertx.java.tests.newtests;

import org.junit.After;
import org.junit.Before;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.VertxFactory;
import org.vertx.java.core.file.impl.ClasspathPathResolver;

import java.net.URL;
import java.nio.file.Path;

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

  protected String findFileOnClasspath(String fileName) {
    URL url = getClass().getClassLoader().getResource(fileName);
    if (url == null) {
      throw new IllegalArgumentException("Cannot find file " + fileName + " on classpath");
    }
    Path path = ClasspathPathResolver.urlToPath(url).toAbsolutePath();
    return path.toString();
  }
}
