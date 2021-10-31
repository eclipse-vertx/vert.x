package io.vertx.core.impl.jvm;

/**
 * Utility class which provides JDK backport and utility methods.
 */
public final class JavaCompatUtil {

  private static final String VIRTUAL_THREAD_CLASSNAME = "java.lang.VirtualThread";

  private JavaCompatUtil() {
  }

  /**
   * Check whether the provided thread is a project loom virtual thread.
   * 
   * @param thread
   * @return true if the provided thread is a virtual one.
   */
  public static boolean isVirtual(Thread thread) {
    if (thread == null) {
      return false;
    }
    return VIRTUAL_THREAD_CLASSNAME.equals(thread.getClass().getName());
  }

}