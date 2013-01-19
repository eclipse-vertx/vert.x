package org.vertx.java.deploy.impl;

import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Each module (not module instance) is assigned it's own ModuleClassLoader.
 *
 * A ModuleClassLoader can have multiple parents, this always includes the class loader of the module that deployed it
 * (or null if is a top level module), plus the class loaders of any modules that this module includes.
 *
 * If the class to be loaded is a system class, the system classloader is called directly.
 *
 * Otherwise this class loader always tries to the load the class itself. If it can't find the class it iterates
 * through its parents trying to load the class. If none of the parents can find it, the system classloader is tried.
 *
 * When locating resources this class loader always looks for the resources itself, then it asks the parents to look,
 * and finally the system classloader is asked.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleClassLoader extends URLClassLoader {

  private static final Logger log = LoggerFactory.getLogger(ModuleClassLoader.class);

  private final List<ModuleClassLoader> parents = new CopyOnWriteArrayList<>();
  private final ClassLoader system;
  private VerticleFactory factory;

  public ModuleClassLoader(URL[] classpath) {
    super(classpath, null);
    system = getSystemClassLoader();
  }

  public void addParent(ModuleClassLoader parent) {
    parents.add(parent);
  }

  // We load the VerticleFactory class using the module classloader - this allows
  // us to put language implementations in modules
  // And we maintain a single VerticleFactory per classloader
  public synchronized VerticleFactory getVerticleFactory(String factoryName, VerticleManager mgr)
      throws ClassNotFoundException, InstantiationException, IllegalAccessException {
    if (factory == null) {
      Class clazz = loadClass(factoryName);
      factory = (VerticleFactory)clazz.newInstance();
      factory.init(mgr, this);
    }
    return factory;
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
      if (isSystemClass(name)) {
        c = system.loadClass(name);
      } else {
        try {
          c = findClass(name);
        } catch (ClassNotFoundException e) {
          for (ClassLoader parent: parents) {
            try {
              return parent.loadClass(name);
            } catch (ClassNotFoundException e1) {
              // Try the next one
            }
          }
          // If we get here then none of the parents could find it, so try the system
          return system.loadClass(name);
        }
      }
    }
    if (resolve) {
      resolveClass(c);
    }
    return c;
  }

  /*
  A system class is any class whose loading should be delegated to the system class loader
  This includes all JDK classes and all vert.x internal classes. We don't want this stuff to be ever loaded
  by a module class loader
   */
  private boolean isSystemClass(String name) {
    return (name.startsWith("java.") || name.startsWith("com.sun.") || name.startsWith("javax.") ||
            name.startsWith("org.vertx."));
  }

  @Override
  public URL getResource(String name) {
    // First try with this class loader
    URL url = findResource(name);
    if (url == null) {
      //Now try with the parents
      for (ModuleClassLoader parent: parents) {
        url = parent.getResource(name);
        if (url != null) {
          return url;
        }
      }
      // If got here then none of the parents know about it, so try the system
      url = system.getResource(name);
    }
    return url;
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {

    final List<URL> totURLs = new ArrayList<>();

    // Local ones
    addURLs(totURLs, findResources(name));

    // Parent ones
    for (ModuleClassLoader parent: parents) {
      Enumeration<URL> urls = parent.getResources(name);
      addURLs(totURLs, urls);
    }

    // And system too
    addURLs(totURLs, system.getResources(name));

    return new Enumeration<URL>() {
      Iterator<URL> iter = totURLs.iterator();

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

  private void addURLs(List<URL> urls, Enumeration<URL> toAdd) {
    if (toAdd != null) {
      while (toAdd.hasMoreElements()) {
        urls.add(toAdd.nextElement());
      }
    }
  }


}
