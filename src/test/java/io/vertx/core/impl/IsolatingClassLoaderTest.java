package io.vertx.core.impl;

import io.vertx.core.json.JsonObject;
import org.junit.Before;
import org.junit.Test;

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

}
