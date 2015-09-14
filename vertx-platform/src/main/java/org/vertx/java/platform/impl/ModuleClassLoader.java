/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package org.vertx.java.platform.impl;

import org.vertx.java.core.impl.ConcurrentHashSet;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Each module (not module instance) is assigned it's own ModuleClassLoader.
 *
 * A ModuleClassLoader instance can reference zero or more other ModuleClassLoader instances.
 *
 * A ModuleClassLoader instance references another ModuleClassLoader instance if it deploys it or includes it.
 *
 * For each ModuleClassLoader there is a set of ModuleClassLoader instances obtained by walking through the
 * references graph recursively and avoiding loops. This is obtained with the method {@link #getModuleGraph()}
 *
 * For each context the Thread context classloader is set to the ModuleClassLoader that created the context.
 *
 * Consequently any class or resource loading that occurs from the same context will have the same set of modules
 * visible to it, which will be the same as the set of modules visible to the ModuleClassLoader of the module that
 * created it.
 *
 * We support two different load orders for modules - either loading with the platform loader first or
 * loading with the module classloader first. This is configurable using the load-from-module-first module
 * field.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleClassLoader extends URLClassLoader {

  private static final Logger log = LoggerFactory.getLogger(ModuleClassLoader.class);

  public final String modID;
  private final Set<ModuleReference> references = new ConcurrentHashSet<>();
  private final ClassLoader platformClassLoader;
  private final boolean loadFromModuleFirst;
  private Set<ModuleClassLoader> modGraph;

  public ModuleClassLoader(String modID, ClassLoader platformClassLoader, URL[] classpath,
                           boolean loadFromModuleFirst) {
    super(classpath);
    this.modID = modID;
    this.platformClassLoader = platformClassLoader;
    this.loadFromModuleFirst = loadFromModuleFirst;
  }

  public synchronized boolean addReference(ModuleReference reference) {
    if (!references.contains(reference)) {
      references.add(reference);
      modGraph = null;
      return true;
    } else {
      return false;
    }
  }

  public void close() {
    for (ModuleReference ref: references) {
      ref.decRef();
    }
    references.clear();
  }

  @Override
  public void addURL(URL url) {
    super.addURL(url);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    Class<?> c = findLoadedClass(name);
    if (c != null) {
      return c;
    }
    if (loadFromModuleFirst) {
      try {
        c = loadFromModule(name);
      } catch (ClassNotFoundException e) {
        c = platformClassLoader.loadClass(name);
      }
    } else {
      try {
        c = platformClassLoader.loadClass(name);
      } catch (ClassNotFoundException e) {
        c = loadFromModule(name);
      }
    }
    if (resolve) {
      resolveClass(c);
    }
    return c;
  }

  private synchronized Class<?> loadFromModule(String name) throws ClassNotFoundException {
    Class<?> c = null;
    Set<ModuleClassLoader> toWalk = getModulesToWalk();
    for (ModuleClassLoader cl: toWalk) {
      c = cl.doLoadClass(name);
      if (c != null) {
        break;
      }
    }
    if (c == null) {
      throw new ClassNotFoundException(name);
    }
    return c;
  }

  protected Class<?> doLoadClass(String name) {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
      try {
        c = findClass(name);
      } catch (ClassNotFoundException e) {
        return null;
      } catch (LinkageError le) {
        c = findLoadedClass(name);
        if (c == null) {
          throw le;
        }
      }
    }
    return c;
  }

  private Set<ModuleClassLoader> getModulesToWalk() {
    ClassLoader mcl = Thread.currentThread().getContextClassLoader();
    if (mcl instanceof ModuleClassLoader) {
      ModuleClassLoader mmcl = (ModuleClassLoader)mcl;
      return mmcl.getModuleGraph();
    } else {
      return getModuleGraph();
    }
  }

  private Set<ModuleClassLoader> getModuleGraph() {
    if (modGraph == null) {
      modGraph = new ConcurrentHashSet<ModuleClassLoader>();
      modGraph.add(this);
      computeModules(modGraph);
    }
    return modGraph;
  }

  private void computeModules(Set<ModuleClassLoader> mods) {
    // We do a depth first search and refuse to go down paths we've already walked to avoid getting stuck in a loop
    for (ModuleReference mod: references) {
      if (!mods.contains(mod.mcl)) {
        mods.add(mod.mcl);
        mod.mcl.computeModules(mods);
      }
    }
  }

  @Override
  public synchronized URL getResource(String name) {
    URL url = platformClassLoader.getResource(name);
    if (url == null) {
      Set<ModuleClassLoader> toWalk = getModulesToWalk();
      for (ModuleClassLoader cl: toWalk) {
        url = cl.findResource(name);
        if (url != null) {
          return url;
        }
      }
    }
    return url;
  }


  @Override
  public synchronized Enumeration<URL> getResources(String name) throws IOException {
    final List<URL> totURLs = new ArrayList<>();

    // And platform class loader too
    addURLs(totURLs, platformClassLoader.getResources(name));

    Set<ModuleClassLoader> toWalk = getModulesToWalk();
    for (ModuleClassLoader cl: toWalk) {
      Enumeration<URL> urls = cl.findResources(name);
      addURLs(totURLs, urls);
    }

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
    } catch (IOException ignore) {
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

  private static final class LinkedHashSet<T> implements Set<T> {

    private final Object obj = new Object();

    private final Map<T, Object> map = new LinkedHashMap<>();

    @Override
    public int size() {
      return map.size();
    }

    @Override
    public boolean isEmpty() {
      return map.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
      return map.containsKey(o);
    }

    @Override
    public Iterator<T> iterator() {
      return map.keySet().iterator();
    }

    @Override
    public Object[] toArray() {
      return map.keySet().toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
      return map.keySet().toArray(a);
    }

    @Override
    public boolean add(T t) {
      map.put(t, obj);
      return true;
    }

    @Override
    public boolean remove(Object o) {
      return map.remove(o) != null;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
      return map.keySet().containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
      return false;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
      return false;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
      return false;
    }

    @Override
    public void clear() {
      map.clear();
    }

  }


}
