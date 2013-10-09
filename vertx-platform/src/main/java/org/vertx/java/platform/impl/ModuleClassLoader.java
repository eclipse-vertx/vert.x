package org.vertx.java.platform.impl;

import org.vertx.java.core.impl.ConcurrentHashSet;

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
 * The parents map to the set of modules that hold a reference to this module - this is important when unloading.
 *
 * We also maintain a set called allToWalk - this comprises the parents plus the set of modules which include
 * the module of this module classloader. We need this, because when walking the graph of module classloaders to
 * load a class the relationship between a module and an included module is two way - i.e. a module can see all the
 * classes in a module it includes, but also a module that is included can see the classes in (and parents of) the
 * modules that include it. This is important for modules which need to dynamically instantiate classes (e.g. Spring)
 * but those classes are not available in the module itself but only in the module that includes it (or its parents).
 *
 * This class loader always tries to load the class with the platform classloader itself, if it can't find it there it
 * then tries to the load the class itself,  if it still can't find the class it iterates
 * through its parents trying to load the class.
 *
 * The same search order is used when locating resources.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ModuleClassLoader extends URLClassLoader {

  // When loading resources or classes we need to catch any circular dependencies
  private static ThreadLocal<Set<ModuleClassLoader>> circDepTL = new ThreadLocal<>();
  // And we need to keep track of the recurse depth so we know when we can remove the thread local
  private static ThreadLocal<Integer> recurseDepth = new ThreadLocal<>();

  private final Set<ModuleReference> parents = new ConcurrentHashSet<>();
  private final Set<ModuleReference> allToWalk = Collections.synchronizedSet(new WeakHashSet<ModuleReference>());
  private final ClassLoader platformClassLoader;
  private boolean loadResourcesFromTCCL = false;
  private String modID;

  public ModuleClassLoader(String modID, ClassLoader platformClassLoader, URL[] classpath, boolean loadResourcesFromTCCL) {
    super(classpath);
    this.modID = modID;
    this.platformClassLoader = platformClassLoader;
    this.loadResourcesFromTCCL = loadResourcesFromTCCL;
  }

  public synchronized void addParent(ModuleReference parent) {
    parents.add(parent);
    allToWalk.add(parent);
  }

  public synchronized void addIncludingModule(ModuleReference including) {
    allToWalk.add(including);
  }

  public void close() {
    clearParents();
  }

  private void clearParents() {
    for (ModuleReference parent: parents) {
      parent.decRef();
    }
    parents.clear();
    allToWalk.clear();
  }

  @Override
  protected synchronized Class<?> loadClass(String name, boolean resolve)
      throws ClassNotFoundException {

    Class<?> c;
    try {
      c = platformClassLoader.loadClass(name);
    } catch (ClassNotFoundException e) {
      c = doLoadClass(name);
      if (c == null) {
        throw new ClassNotFoundException(name);
      }
    }
    if (resolve) {
      resolveClass(c);
    }
    return c;
  }

  protected Class<?> doLoadClass(String name) {
    Class<?> c = findLoadedClass(name);
    if (c == null) {
      try {
        // First try and load the class with the module classloader
        c = findClass(name);
      } catch (ClassNotFoundException e) {
        // Not found - maybe the parent class loaders can load it?
        try {
          // Detect circular hierarchy
          incRecurseDepth();
          Set<ModuleClassLoader> walked = getWalked();
          walked.add(this);
          for (ModuleReference parent: allToWalk) {
            if (!checkAlreadyWalked(walked, parent)) {
              c = parent.mcl.doLoadClass(name);
              if (c != null) {
                break;
              }
            }
          }
          walked.remove(this);
        } finally {
          // Make sure we clear the thread locals afterwards
          checkClearTLs();
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

  private boolean checkAlreadyWalked(Set<ModuleClassLoader> walked, ModuleReference mr) {
    return walked.contains(mr.mcl);
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
  public synchronized URL getResource(String name) {
    return doGetResource(name, true);
  }

  private URL doGetResource(String name, boolean considerTCCL) {
    incRecurseDepth();
    try {
      URL url = platformClassLoader.getResource(name);
      if (url == null) {
        // First try with this class loader
        url = findResource(name);
        if (url == null) {
          // Detect circular hierarchy
          Set<ModuleClassLoader> walked = getWalked();
          walked.add(this);

          //Now try with the parents
          for (ModuleReference parent: allToWalk) {
            if (!checkAlreadyWalked(walked, parent)) {
              url = parent.mcl.doGetResource(name, considerTCCL);
              if (url != null) {
                return url;
              }
            }
          }

          walked.remove(this);

          // There's now a workaround due to dodgy classloading in Jython
          // https://github.com/vert-x/mod-lang-jython/issues/7
          // It seems that Jython doesn't always ask the correct classloader to load resources from modules
          // to workaround this we can, if the resource is not found, and the TCCL is different from this classloader
          // to ask the TCCL to load the class - the TCCL should always be set to the moduleclassloader of the actual
          // module doing the import
          if (considerTCCL && loadResourcesFromTCCL) {
            // We need to clear wallked as otherwise can get a circular dependency error when there's no
            // real circuular dependency
            walked.clear();
            ModuleClassLoader tccl = (ModuleClassLoader)Thread.currentThread().getContextClassLoader();
            if (tccl != this) {
              // Call with considerTCCL = false to prevent infinite recursion
              url = tccl.doGetResource(name, false);
              if (url != null) {
                return url;
              }
            }
          }
        }
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
  public synchronized Enumeration<URL> getResources(String name) throws IOException {
    final List<URL> totURLs = new ArrayList<>();

    // And platform class loader too
    addURLs(totURLs, platformClassLoader.getResources(name));

    // Local ones
    addURLs(totURLs, findResources(name));

    try {
      // Detect circular hierarchy
      incRecurseDepth();
      Set<ModuleClassLoader> walked = getWalked();
      walked.add(this);

      // Parent ones
      for (ModuleReference parent: allToWalk) {
        if (!checkAlreadyWalked(walked, parent)) {
          Enumeration<URL> urls = parent.mcl.getResources(name);
          addURLs(totURLs, urls);
        }
      }
      walked.remove(this);
    } finally {
      checkClearTLs();
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

  private static final class WeakHashSet<T> implements Set<T> {

    private Map<T, Object> map = new WeakHashMap<>();
    private final Object obj = new Object();

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
