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
 * A ModuleClassLoader can have multiple parents, this always includes the class loader of the module that deployed it
 * (or null if is a top level module), plus the class loaders of any modules that this module includes.
 *
 * This class loader always tries to the load the class itself. If it can't find the class it iterates
 * through its parents trying to load the class. If none of the parents can find it, the platform class loader classloader is tried.
 *
 * When locating resources this class loader always looks for the resources itself, then it asks the parents to look,
 * and finally the platform class loader classloader is asked.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleClassLoader extends URLClassLoader {

  private static final Logger log = LoggerFactory.getLogger(ModuleClassLoader.class);

  // When running in an IDE we want to always try and load from the platform classloader first
  // This allows in-container tests running in the IDE to immediately see changes to any resources in the module
  // without having to rebuild the module into the mods directory every time
  public static boolean reverseLoadOrder = true;

  // When loading resources or classes we need to catch any circular dependencies
  private static ThreadLocal<Set<ModuleClassLoader>> circDepTL = new ThreadLocal<>();
  // And we need to keep track of the recurse depth so we know when we can remove the thread local
  private static ThreadLocal<Integer> recurseDepth = new ThreadLocal<>();

  private final Set<ModuleReference> parents = new ConcurrentHashSet<>();
  private final ClassLoader platformClassLoader;

  public ModuleClassLoader(ClassLoader platformClassLoader, URL[] classpath) {
    super(classpath);
    this.platformClassLoader = platformClassLoader;
  }

  public void addParent(ModuleReference parent) {
    parents.add(parent);
  }

  public void close() {
    clearParents();
  }

  private void clearParents() {
    for (ModuleReference parent: parents) {
      parent.decRef();
    }
    parents.clear();
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
      if (reverseLoadOrder) {
        try {
          c = platformClassLoader.loadClass(name);
        } catch (ClassNotFoundException e) {
        }
      }
      if (c == null) {
        try {
          // First try and load the class with the module classloader
          c = findClass(name);
          if (resolve) {
            resolveClass(c);
          }
        } catch (ClassNotFoundException e) {
          // Not found - maybe the parent class loaders can load it?
          try {
            // Detect circular hierarchy
            incRecurseDepth();
            Set<ModuleClassLoader> walked = getWalked();
            walked.add(this);
            for (ModuleReference parent: parents) {
              checkAlreadyWalked(walked, parent);
              try {
                // Try with the parent
                c = parent.mcl.loadClass(name);
                break;
              } catch (ClassNotFoundException e1) {
                // Try the next one
              }
            }
          } finally {
            // Make sure we clear the thread locals afterwards
            checkClearTLs();
          }
          if (c == null) {
            if (reverseLoadOrder) {
              throw e;
            } else {
              // If we get here then the module classloaders couldn't load it so we try the platform class loader
              c = platformClassLoader.loadClass(name);
            }
          }
        }
      }
    }
    return c;
  }

  private Set<ModuleClassLoader> getWalked() {
    Set<ModuleClassLoader> walked = circDepTL.get();
    if (walked == null) {
      walked = new HashSet<>();
      circDepTL.set(walked);
    }
    return walked;
  }

  private void checkAlreadyWalked(Set<ModuleClassLoader> walked, ModuleReference mr) {
    if (walked.contains(mr.mcl)) {
      // Break the circular dep and reduce the ref count
      // We need to do this on ALL the parents in case there is another circular dep there
      clearParents();
      throw new IllegalStateException("Circular dependency in module includes.");
    }
  }

  private void incRecurseDepth() {
    Integer depth = recurseDepth.get();
    recurseDepth.set(depth == null ? 1 : depth + 1);
  }

  private int decRecurseDepth() {
    Integer depth = recurseDepth.get();
    depth = depth - 1;
    recurseDepth.set(depth);
    return depth;
  }

  @Override
  public URL getResource(String name) {
    incRecurseDepth();
    try {
      // First try with this class loader
      URL url = findResource(name);
      if (url == null) {
        // Detect circular hierarchy
        Set<ModuleClassLoader> walked = getWalked();
        walked.add(this);
        //Now try with the parents
        for (ModuleReference parent: parents) {
          checkAlreadyWalked(walked, parent);
          url = parent.mcl.getResource(name);
          if (url != null) {
            return url;
          }
        }
        // If got here then none of the parents know about it, so try the platform class loader
        url = platformClassLoader.getResource(name);
      }
      return url;
    } finally {
      checkClearTLs();
    }
  }

  private void checkClearTLs() {
    if (decRecurseDepth() == 0) {
      circDepTL.remove();
      recurseDepth.remove();
    }
  }

  @Override
  public Enumeration<URL> getResources(String name) throws IOException {
    final List<URL> totURLs = new ArrayList<>();

    // Local ones
    addURLs(totURLs, findResources(name));

    try {
      // Detect circular hierarchy
      incRecurseDepth();
      Set<ModuleClassLoader> walked = getWalked();
      walked.add(this);

      // Parent ones
      for (ModuleReference parent: parents) {
        checkAlreadyWalked(walked, parent);
        Enumeration<URL> urls = parent.mcl.getResources(name);
        addURLs(totURLs, urls);
      }
    } finally {
      checkClearTLs();
    }

    // And platform class loader too
    addURLs(totURLs, platformClassLoader.getResources(name));

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
