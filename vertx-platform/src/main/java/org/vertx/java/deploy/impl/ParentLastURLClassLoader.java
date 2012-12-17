/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

/**
 * @author Adapted from http://stackoverflow.com/questions/5445511/how-do-i-create-a-parent-last-child-first-classloader-in-java-or-how-to-overr
 */
public class ParentLastURLClassLoader extends URLClassLoader {

  private ClassLoader system;

  private static final Logger log = LoggerFactory.getLogger(ParentLastURLClassLoader.class);

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
           name.startsWith("com.sun."));
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
    final List<URL> urls = new ArrayList<URL>();
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
