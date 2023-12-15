package io.vertx.core.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;

public class IsolatingClassLoaderTest {

  private IsolatingClassLoaderTest.MockClassloader parent;

  @Before
  public void setUp() throws Exception {
    parent = new MockClassloader();
  }

  @Test
  public void testLoadClassIsolatedSpecific() throws Exception {
    List<String> isolated = new LinkedList<>();
    isolated.add("io.netty.buffer.ByteBuf");

    final URL[] urLs = URLClassLoader.class.cast(this.getClass().getClassLoader()).getURLs();

    final IsolatingClassLoader isolatingClassLoader = new IsolatingClassLoader(urLs, parent,
      isolated);

    isolatingClassLoader.loadClass("io.netty.buffer.ByteBuf");
    assertFalse(parent.loaded.contains("io.netty.buffer.ByteBuf"));
  }

  @Test
  public void testLoadClassIsolatedWildcard() throws Exception {
    List<String> isolated = new LinkedList<>();
    isolated.add("io.netty.*");

    final URL[] urLs = URLClassLoader.class.cast(this.getClass().getClassLoader()).getURLs();

    final IsolatingClassLoader isolatingClassLoader = new IsolatingClassLoader(urLs, parent,
      isolated);

    isolatingClassLoader.loadClass("io.netty.buffer.ByteBuf");
    assertFalse(parent.loaded.contains("io.netty.buffer.ByteBuf"));
    assertTrue(parent.loaded.stream().noneMatch(s -> s.contains("io.netty")));
  }

  private class MockClassloader extends URLClassLoader {

    private final Set<String> loaded;

    private MockClassloader() {
      super(new URL[]{});
      loaded = new HashSet<>();
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      this.loaded.add(name);
      return super.loadClass(name, resolve);
    }
  }
}
