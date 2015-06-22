package io.vertx.test.core;

import io.vertx.core.impl.IsolatingClassLoader;
import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;
import user.VerticleImpl;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Scanner;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link io.vertx.core.impl.IsolatingClassLoader}
 */
public class IsolatingClassLoaderTest {

  private String basePath = "src/test/resources/icl";
  private String resourceName = "resource.json";
  private URL url1;
  private URL url2;
  private URL url3;
  private URLClassLoader ucl;
  private IsolatingClassLoader icl;

  @Before
  public void setUp() throws Exception {

    url1 = new File(basePath, "pkg1").toURI().toURL();
    url2 = new File(basePath, "pkg2").toURI().toURL();
    url3 = new File(basePath, "pkg3").toURI().toURL();

    ucl = new URLClassLoader(new URL[]{url2, url3});
    icl = new IsolatingClassLoader(new URL[]{url1}, ucl);

  }

  @Test
  public void testGetResource() throws Exception {

    URL resource = ucl.getResource(resourceName);
    checkResource(url2, resource);

    resource = icl.getResource(resourceName);
    checkResource(url1, resource);

  }

  @Test
  public void testGetResourceNull() throws Exception {

    resourceName = "null_resource";
    URL resource = ucl.getResource(resourceName);
    assertNull(resource);

    resource = icl.getResource(resourceName);
    assertNull(resource);

  }

  @Test
  public void testGetResources() throws Exception {

    Enumeration<URL> resources = ucl.getResources(resourceName);
    List<URL> list = Collections.list(resources);
    assertEquals(2, list.size());
    checkResource(url2, list.get(0));
    checkResource(url3, list.get(1));

    resources = icl.getResources(resourceName);
    list = Collections.list(resources);
    assertEquals(3, list.size());
    checkResource(url1, list.get(0));
    checkResource(url2, list.get(1));
    checkResource(url3, list.get(2));

  }

  private void checkResource(URL expected, URL resource) throws Exception {
    assertEquals(expected.toString() + resourceName, resource.toString());
  }

  @Test
  public void testGetResourcesNull() throws Exception {

    resourceName = "null_resource";
    Enumeration<URL> resources = ucl.getResources(resourceName);
    List<URL> list = Collections.list(resources);
    assertEquals(0, list.size());

    resources = icl.getResources(resourceName);
    list = Collections.list(resources);
    assertEquals(0, list.size());

  }

  @Test
  public void testGetResourceAsStream() throws Exception {

    testGetResourceAsStream(2, ucl);
    testGetResourceAsStream(1, icl);

  }

  private void testGetResourceAsStream(long ver, ClassLoader cl) throws Exception {

    try (InputStream is = cl.getResourceAsStream(resourceName)) {
      assertNotNull(is);

      try (Scanner scanner = new Scanner(is, "UTF-8").useDelimiter("\\A")) {
        assertTrue(scanner.hasNext());
        JsonObject json = new JsonObject(scanner.next());
        assertEquals(ver, json.getLong("ver", -1L).longValue());
      }
    }

  }

  /**
   * This reproduces the -redeploy issue with Groovy or Metrics that have a current classloader that has an URL
   * classloader that has the extension class (GroovyVerticle or Metrics classes) in its URL[].
   *
   * The problem is that in both cases, the class executing the code does not have the same class loader than
   * the object it manipulates
   *
   * 1/ GroovyVerticleFactory loaded from the current classloader instantiates a GroovyVerticle that has
   * a different GroovyVerticle class object as it is loaded from IsolatingClassLoader
   *
   * 2/ MetricsServiceImpl executed from the Verticle loaded from the IsolatingClassLoader has metrics classes
   * different than the classes used by the Vertx instance.
   *
   * Both issues are the same but executes from different perspectives.
   *
   * @throws Exception
   */
  @Test
  public void testReproducer() throws Exception {

    // Isolating classloader configuration similar to the -redeploy case
    URLClassLoader current = (URLClassLoader) Thread.currentThread().getContextClassLoader();
    IsolatingClassLoader icl = new IsolatingClassLoader(current.getURLs(), current);


    Class clazz = icl.loadClass("user.VerticleImpl");
    assertNotSame(VerticleImpl.class, clazz);
    Object instance = clazz.newInstance();

    // This can be casted
    JsonObject core = (JsonObject) clazz.getDeclaredField("core").get(instance);

    // This makes a classcast exception
    IsolatingClassLoaderTest ext = (IsolatingClassLoaderTest) clazz.getDeclaredField("ext").get(instance);


  }

}
