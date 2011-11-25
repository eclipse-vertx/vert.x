package org.vertx.java.core.app;

import org.vertx.java.core.logging.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;

/**
 * @author Adapted from http://stackoverflow.com/questions/5445511/how-do-i-create-a-parent-last-child-first-classloader-in-java-or-how-to-overr
 */
public class ParentLastURLClassLoader extends URLClassLoader {

  private ClassLoader system;

  private static final Logger log = Logger.getLogger(ParentLastURLClassLoader.class);

  public ParentLastURLClassLoader(URL[] classpath, ClassLoader parent) {
    super(classpath, parent);
    system = getSystemClassLoader();
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
      if (isSystemClass(name)) {
        c = super.loadClass(name, resolve);
      } else {
        try {
          c = findClass(name);
        } catch (ClassNotFoundException e) {
          c = super.loadClass(name, resolve);
        }
      }
    }
    if (resolve) {
      resolveClass(c);
    }
    return c;
  }

  private boolean isSystemClass(String name) {
    return (name.startsWith("org.vertx.") || name.startsWith("java.") || name.startsWith("javax.") ||
           name.startsWith("com.sun.") || name.startsWith("org.jruby."));
  }

  @Override
  public URL getResource(String name) {
    URL url = findResource(name);
    if (url == null) {
      url = super.getResource(name);
    }
    return url;
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    Enumeration<URL> localUrls = findResources(name);
    Enumeration<URL> parentUrls = null;
    if (getParent() != null) {
      parentUrls = getParent().getResources(name);
    }
    final List<URL> urls = new ArrayList<>();
    if (localUrls != null) {
      while (localUrls.hasMoreElements()) {
        urls.add(localUrls.nextElement());
      }
    }
    if (parentUrls != null) {
      while (parentUrls.hasMoreElements()) {
        urls.add(parentUrls.nextElement());
      }
    }
    return new Enumeration<URL>() {
      Iterator<URL> iter = urls.iterator();

      public boolean hasMoreElements() {
        return iter.hasNext();
      }

      public URL nextElement() {
        return iter.next();
      }
    };
  }

  @Override
  public InputStream getResourceAsStream(String name) {
    URL url = getResource(name);
    try {
      return url != null ? url.openStream() : null;
    } catch (IOException e) {
    }
    return null;
  }

}
