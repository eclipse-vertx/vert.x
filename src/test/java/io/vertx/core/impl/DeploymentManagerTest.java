package io.vertx.core.impl;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.Test;
import org.assertj.core.util.Arrays;
import org.junit.Assert;

public class DeploymentManagerTest {

  private static boolean isJarOrZip(File file) {
    String fileName = file.getName().toLowerCase();
    if (fileName.endsWith(".jar") || fileName.endsWith(".zip")) {
      return true;
    }
    return false;
  }

  @Test
  public void extractClasspathTest() throws URISyntaxException{
    for(URL cpURL : DeploymentManager.extractClasspath(getClass().getClassLoader())) {
      File cpEntry = new File(cpURL.toURI());
      Assert.assertTrue("Classpath entry does not exist: " + cpURL, cpEntry.exists());
      if (cpEntry.isFile()) {
        // If classpath entry is a file, it must be a jar or zip
        Assert.assertTrue("Classpath entry is a file but not a zip or jar: " + cpURL, isJarOrZip(cpEntry));
      }
    }
  }

  @Test
  public void extractClasspathWithExplodedJarTest() throws MalformedURLException{
    URL cp = new File("src/test/resources/cpExtraction/exploded").toURI().toURL();
    URLClassLoader ucl = new URLClassLoader(Arrays.array(cp), null);
    for (URL cpURL : DeploymentManager.extractCPByManifest(ucl)) {
      Assert.assertEquals("extractCPByManifest does not handle exploded JARs correct", cp, cpURL);
    }
  }
}
