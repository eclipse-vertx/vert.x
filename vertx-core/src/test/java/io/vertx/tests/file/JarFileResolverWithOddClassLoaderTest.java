package io.vertx.tests.file;

import io.vertx.core.file.impl.FileResolverImpl;
import io.vertx.core.spi.file.FileResolver;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class JarFileResolverWithOddClassLoaderTest {

  @Test
  public void testWhenClassLoaderReturnUrlsForDirectory() throws IOException {
    File webjar = new File("src/test/resources/jars/wc-chatbot-0.1.2.jar");
    Assertions.assertThat(webjar).exists();
    ClassLoader cl = new DecoratedClassLoader(new URLClassLoader(new URL[]{webjar.toURI().toURL()}, Thread.currentThread().getContextClassLoader()));

    ClassLoader orig = Thread.currentThread().getContextClassLoader();
    try (FileResolver resolver = new FileResolverImpl()) {
      Thread.currentThread().setContextClassLoader(cl);
      String fileName = "META-INF/resources/_static/wc-chatbot/0.1.2/LICENSE";
      File resolved = resolver.resolve(fileName);
      Assertions.assertThat(resolved).exists();
    } finally {
      Thread.currentThread().setContextClassLoader(orig);
    }
  }

  /**
   * A somewhat broken classloader returning an URL even for directory included in the given jars.
   */
  private static class DecoratedClassLoader extends ClassLoader {
    private final URL[] urls;

    public DecoratedClassLoader(URLClassLoader parent) {
      super(parent);
      this.urls = parent.getURLs();
    }

    @Override
    protected URL findResource(String name) {
      if (! name.contains("META-INF/resources")) {
        return super.findResource(name);
      }
      for (URL url : urls) {
        if (url.getFile().endsWith(".jar")) {
          try {
            return new URL("jar:" + url + "!/" + name);
          } catch (MalformedURLException e) {
            return super.findResource(name);
          }
        }
      }
      return super.findResource(name);
    }
  }
}
