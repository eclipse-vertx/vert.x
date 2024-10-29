package io.vertx.core.impl.transports;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.net.impl.SocketAddressImpl;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.net.SocketAddress;
import java.nio.file.Path;

import static java.lang.invoke.MethodType.methodType;

class UnixDomainSocketNioTransport {

  private static final Logger LOG = LoggerFactory.getLogger(UnixDomainSocketNioTransport.class);

  private final Class<?> unixDomainSocketAddressClass;
  private final MethodHandle ofMethodHandle;
  private final MethodHandle getPathMethodHandle;

  private UnixDomainSocketNioTransport(Class<?> unixDomainSocketAddressClass, MethodHandle ofMethodHandle, MethodHandle getPathMethodHandle) {
    this.unixDomainSocketAddressClass = unixDomainSocketAddressClass;
    this.ofMethodHandle = ofMethodHandle;
    this.getPathMethodHandle = getPathMethodHandle;
  }

  static UnixDomainSocketNioTransport load() {
    Class<?> unixDomainSocketAddressClass;
    MethodHandle ofMethodHandle;
    MethodHandle getPathMethodHandle;
    if (PlatformDependent.javaVersion() >= 16) {
      try {
        unixDomainSocketAddressClass = Class.forName("java.net.UnixDomainSocketAddress");
        MethodHandles.Lookup lookup = MethodHandles.publicLookup();
        ofMethodHandle = lookup.findStatic(unixDomainSocketAddressClass, "of", methodType(unixDomainSocketAddressClass, Path.class));
        getPathMethodHandle = lookup.findVirtual(unixDomainSocketAddressClass, "getPath", methodType(Path.class));
        return new UnixDomainSocketNioTransport(unixDomainSocketAddressClass, ofMethodHandle, getPathMethodHandle);
      } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException e) {
        LOG.warn("JDK Unix Domain Socket support is not available", e);
      }
    }
    return null;
  }

  SocketAddress convert(io.vertx.core.net.SocketAddress address) {
    try {
      return (SocketAddress) ofMethodHandle.invoke(Path.of(address.path()));
    } catch (Throwable cause) {
      rethrowIfPossible(cause);
      throw new LinkageError("java.net.UnixDomainSocketAddress.of not available", cause);
    }
  }

  boolean isUnixDomainSocketAddress(SocketAddress address) {
    return unixDomainSocketAddressClass.isAssignableFrom(address.getClass());
  }

  io.vertx.core.net.SocketAddress convert(SocketAddress address) {
    try {
      Path path = (Path) getPathMethodHandle.invoke(address);
      return new SocketAddressImpl(path.toAbsolutePath().toString());
    } catch (Throwable cause) {
      rethrowIfPossible(cause);
      throw new LinkageError("java.net.UnixDomainSocketAddress.getPath not available", cause);
    }
  }

  private static void rethrowIfPossible(Throwable cause) {
    if (cause instanceof Error) {
      throw (Error) cause;
    }
    if (cause instanceof RuntimeException) {
      throw (RuntimeException) cause;
    }
  }
}
