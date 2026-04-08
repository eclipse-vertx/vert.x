/*
 * Copyright (c) 2011-2019 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.test.core;

import io.netty.util.internal.PlatformDependent;
import io.vertx.core.*;
import io.vertx.core.internal.VertxInternal;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.net.*;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.spi.tracing.VertxTracer;
import io.vertx.core.tracing.TracingOptions;
import io.vertx.core.transport.Transport;
import io.vertx.test.fakecluster.FakeClusterManager;
import junit.framework.AssertionFailedError;
import org.junit.Assert;
import org.junit.Rule;

import javax.net.ssl.SSLContext;
import java.lang.ref.WeakReference;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class VertxTestBase extends AsyncTestBase {

  /**
   * Test mode, note: this is temporary until migration is done.
   */
  public enum ReportMode {

    /**
     * The legacy / usual test mode
     */
    LEGACY,

    /**
     * Enable stateless assertions, relying on the thread to report failures:
     * <ul>
     *   <li>JUnit thread naturally reports failures to the runner</li>
     *   <li>Vert.x thread relies on context task execution to report failure to Vert.x exception handler</li>
     * </ul>
     * This mode should only be used when migrated a test to spot incorrect usage of AsyncTestBase assertions made
     * from a non instrumented thread that would miss test checks.
     */
    STATELESS,

    /**
     * Throw an exception upon any assertion, this mode should be used when the test is migrated, ensuring that
     * the various reporting methods of {@link AsyncTestBase} are never called anymore.
     */
    FORBIDDEN
  }

  public static final Transport TRANSPORT;
  public static final boolean USE_DOMAIN_SOCKETS = Boolean.getBoolean("vertx.useDomainSockets");
  public static final boolean USE_JAVA_MODULES = VertxTestBase.class.getModule().isNamed();
  private static final Logger log = LoggerFactory.getLogger(VertxTestBase.class);
  protected static final String[] ENABLED_CIPHER_SUITES;

  static {

    String[] suites = new String[0];
    try {
      suites = SSLContext.getDefault().getSocketFactory().getSupportedCipherSuites();
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    }
    ENABLED_CIPHER_SUITES = suites;

    Transport transport = null;
    String transportName = System.getProperty("vertx.transport");
    if (transportName != null) {
      String t = transportName.toLowerCase(Locale.ROOT);
      switch (t) {
        case "jdk":
          transport = Transport.NIO;
          break;
        case "kqueue":
          transport = Transport.KQUEUE;
          break;
        case "epoll":
          transport = Transport.EPOLL;
          break;
        case "io_uring":
          transport = Transport.IO_URING;
          break;
        default:
          transport = new Transport() {
            @Override
            public String name() {
              return transportName;
            }
            @Override
            public boolean available() {
              return false;
            }
            @Override
            public Throwable unavailabilityCause() {
              return new RuntimeException("Transport " + transportName + " does not exist");
            }
            @Override
            public io.vertx.core.spi.transport.Transport implementation() {
              throw new IllegalStateException("Transport " + transportName + " does not exist");
            }
          };
      }
      if (transport == null) {
        transport = new Transport() {
          @Override
          public String name() {
            return t;
          }
          @Override
          public boolean available() {
            return false;
          }
          @Override
          public Throwable unavailabilityCause() {
            return new IllegalStateException("Transport " + t + " not available");
          }
          @Override
          public io.vertx.core.spi.transport.Transport implementation() {
            return null;
          }
        };
      }
    } else {
      transport = Transport.NIO;
    }
    TRANSPORT = transport;
  }

  @Rule
  public RepeatRule repeatRule = new RepeatRule();

  @Rule
  public FileDescriptorLeakDetectorRule fileDescriptorLeakDetectorRule = new FileDescriptorLeakDetectorRule();

  private final ReportMode reportMode;
  protected Vertx vertx;
  protected Vertx[] vertices;
  private List<Vertx> created;
  private Thread junitThread;
  private Handler<Throwable> failureBridgeHandler;

  public VertxTestBase(ReportMode reportMode) {
    this.reportMode = reportMode;
  }

  public VertxTestBase() {
    this(ReportMode.LEGACY);
  }

  @Override
  protected void disableThreadChecks() {
    if (reportMode != ReportMode.LEGACY) {
      // Do nothing as we actually want to ensure that this is either
      // - the JUnit main thread
      // - a vertx context thread that we can fail to
    } else {
      super.disableThreadChecks();
    }
  }

  @Override
  protected void checkThread() {
    switch (reportMode) {
      case LEGACY:
        super.checkThread();
        break;
      case STATELESS:
        if (Thread.currentThread() == junitThread) {
          // Ok
        } else {
          Context current = Vertx.currentContext();
          if (current == null) {
            System.out.println("Running test assertion from un-associated thread: " + Thread.currentThread());
            new Exception().printStackTrace(System.out);
          } else {
            Handler<Throwable> handler = current.owner().exceptionHandler();
            if (handler != failureBridgeHandler) {
              System.out.println("Asserting from a vertx thread that is not relaying failures to the failure handler: " + Thread.currentThread());
              new Exception().printStackTrace(System.out);
            }
          }
        }
        break;
      case FORBIDDEN:
        throw new UnsupportedOperationException("Strictly forbidden to call an assertion on AsyncTestBase");
    }
  }

  @Override
  void handleThrowable(Throwable t) {
    switch (reportMode) {
      case STATELESS:
        PlatformDependent.throwException(t);
        break;
      case LEGACY:
        super.handleThrowable(t);
        break;
    }
  }

  protected void vinit() {
    vertx = null;
    vertices = null;
    created = null;
  }

  public void setUp() throws Exception {
    failureBridgeHandler = new FailureBridgeHandler(this);
    junitThread = Thread.currentThread();
    super.setUp();
    vinit();
    VertxOptions options = getOptions();
    boolean nativeTransport = options.getPreferNativeTransport();
    vertx = vertx(options);
    if (nativeTransport && !vertx.isNativeTransportEnabled()) {
      if (!vertx.isNativeTransportEnabled()) {
        AssertionFailedError afe = new AssertionFailedError("Expected native transport");
        Throwable cause = vertx.unavailableNativeTransportCause();
        if (cause != null) {
          afe.initCause(cause);
        }
        throw afe;
      }
      Assert.assertTrue(vertx.isNativeTransportEnabled());
    }
  }

  protected VertxTracer getTracer() {
    return null;
  }

  protected VertxMetricsFactory getMetrics() {
    return null;
  }

  protected VertxOptions getOptions() {
    return new VertxOptions();
  }

  protected void tearDown() throws Exception {
    junitThread = null;
    if (created != null) {
      close(created);
    }
    FakeClusterManager.reset(); // Bit ugly
    super.tearDown();
  }

  @Override
  public void await(long delay, TimeUnit timeUnit) {
    super.await(delay, timeUnit);
  }

  protected void close(List<Vertx> instances) throws Exception {
    CountDownLatch latch = new CountDownLatch(instances.size());
    for (Vertx instance : instances) {
      instance.close().onComplete(ar -> {
        if (ar.failed()) {
          log.error("Failed to shutdown vert.x", ar.cause());
        }
        latch.countDown();
      });
    }
    Assert.assertTrue(latch.await(180, TimeUnit.SECONDS));
  }

  protected final boolean isVirtualThreadAvailable() {
    return ((VertxInternal)vertx).isVirtualThreadAvailable();
  }

  /**
   * @return create a blank new Vert.x instance with no options closed when tear down executes.
   */
  protected Vertx vertx() {
    return vertx(new VertxOptions());
  }

  protected VertxBuilder createVertxBuilder(VertxOptions options) {
    VertxBuilder builder = Vertx.builder();
    VertxTracer<?, ?> tracer = getTracer();
    if (tracer != null) {
      builder.withTracer(o -> tracer);
      options = new VertxOptions(options).setTracingOptions(new TracingOptions());
    }
    VertxMetricsFactory metrics = getMetrics();
    if (metrics != null) {
      builder.withMetrics(metrics);
      options = new VertxOptions(options).setMetricsOptions(new MetricsOptions().setEnabled(true));
    }
    builder.withTransport(TRANSPORT);
    return builder.with(options);
  }

  protected Vertx createVertx(VertxOptions options) {
    Vertx vertx = createVertxBuilder(options).build();
    if (TRANSPORT != Transport.NIO) {
      if (!vertx.isNativeTransportEnabled()) {
        Assert.fail("Native transport is not enabled: " + vertx.unavailableNativeTransportCause());
      }
    }
    return vertx;
  }

  /**
   * @return create a blank new Vert.x instance with @{@code options} closed when tear down executes.
   */
  protected Vertx vertx(VertxOptions options) {
    return vertx(() -> createVertx(options));
  }

  protected Vertx vertx(Supplier<Vertx> supplier) {
    if (created == null) {
      created = Collections.synchronizedList(new ArrayList<>());
    }
    Vertx vertx = supplier.get();
    add(vertx);
    return vertx;
  }

  private void add(Vertx vertx) {
    if (reportMode != ReportMode.LEGACY) {
      vertx.exceptionHandler(failureBridgeHandler);
    }
    created.add(vertx);
  }

  private void handleFailure(Throwable error) {
    super.handleThrowable(error);
  }

  /**
   * Create a blank new clustered Vert.x instance with @{@code options} closed when tear down executes.
   */
  protected Future<Vertx> clusteredVertx(VertxOptions options) {
    return clusteredVertx(options, getClusterManager());
  }

  protected Future<Vertx> clusteredVertx(VertxOptions options, ClusterManager clusterManager) {
    if (created == null) {
      created = Collections.synchronizedList(new ArrayList<>());
    }
    if (clusterManager == null) {
      clusterManager = new FakeClusterManager();
    }
    return createVertxBuilder(options)
      .withClusterManager(clusterManager)
      .buildClustered()
      .andThen(event -> {
        if (event.succeeded()) {
          add(event.result());
        }
      });
  }

  protected ClusterManager getClusterManager() {
    return null;
  }

  protected void startNodes(int numNodes) {
    startNodes(numNodes, getOptions());
  }

  protected void startNodes(int numNodes, VertxOptions options) {
    startNodes(numNodes, options, this::getClusterManager);
  }

  protected void startNodes(int numNodes, Supplier<ClusterManager> clusterManagerSupplier) {
    startNodes(numNodes, new VertxOptions(), clusterManagerSupplier);
  }

  private void startNodes(int numNodes, VertxOptions options, Supplier<ClusterManager> clusterManagerSupplier) {
    vertices = new Vertx[numNodes];
    for (int i = 0; i < numNodes; i++) {
      VertxOptions toUse = new VertxOptions(options);
      toUse.getEventBusOptions().setHost("localhost").setPort(0);
      vertices[i] = clusteredVertx(toUse, clusterManagerSupplier.get())
        .await();
    }
  }

  protected static void setOptions(TCPSSLOptions sslOptions, KeyCertOptions options) {
    sslOptions.setKeyCertOptions(options);
  }

  /**
   * Create a worker verticle for the current Vert.x and return its context.
   *
   * @return the context
   * @throws Exception anything preventing the creation of the worker
   */
  protected Context createWorker() throws Exception {
    CompletableFuture<Context> fut = new CompletableFuture<>();
    vertx.deployVerticle(new AbstractVerticle() {
      @Override
      public void start() throws Exception {
        fut.complete(context);
      }
    }, new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER)).onComplete(ar -> {
      if (ar.failed()) {
        fut.completeExceptionally(ar.cause());
      }
    });
    return fut.get();
  }

  /**
   * Create worker verticles for the current Vert.x and returns the list of their contexts.
   *
   * @param num the number of verticles to create
   * @return the contexts
   * @throws Exception anything preventing the creation of the workers
   */
  protected List<Context> createWorkers(int num) throws Exception {
    List<Context> contexts = new ArrayList<>();
    for (int i = 0;i < num;i++) {
      contexts.add(createWorker());
    }
    return contexts;
  }

  protected void assertOnIOContext(Context context) {
    Context current = Vertx.currentContext();
    assertNotNull(current);
    assertSameEventLoop(context, current);
  }

  /**
   * Reports a failure to the test, this uses a weak reference to avoid keeping a path of references from the cleaner
   * to the test that might contain references to cleanable objects, preventing those cleanable to be reclaimed:
   *
   * --> CleanerImpl#phantomCleanableList
   * --> PhantomCleanableRef#action
   * --> CleanableNetClient.Action#client
   * --> NetClient#vertx
   * --> Vertx#exceptionHandler
   * --> FailureBridgeHandler#test
   * --> NetTest#client
   * --> CleanableNetClient
   */
  private static class FailureBridgeHandler extends WeakReference<VertxTestBase> implements Handler<Throwable> {
    public FailureBridgeHandler(VertxTestBase referent) {
      super(referent);
    }
    @Override
    public void handle(Throwable failure) {
      VertxTestBase test = get();
      if (test != null) {
        test.handleFailure(failure);
      }
    }
  }
}
