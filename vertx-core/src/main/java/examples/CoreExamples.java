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

package examples;

import io.vertx.core.*;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
import io.vertx.core.file.FileProps;
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.internal.logging.Logger;
import io.vertx.core.internal.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.SocketAddress;
import io.vertx.core.spi.VertxMetricsFactory;
import io.vertx.core.spi.VertxTracerFactory;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.core.transport.Transport;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 * Created by tim on 08/01/15.
 */
public class CoreExamples {

  public void example1() {
    Vertx vertx = Vertx.vertx();
  }

  public void example2() {
    Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(40));
  }

  public void example3(HttpServerRequest request) {
    request.response().putHeader("Content-Type", "text/plain").end("some text");
  }

  public void example4(HttpServerRequest request) {
    HttpServerResponse response = request.response();
    response.putHeader("Content-Type", "text/plain");
    response.end("some text");
  }

  public void example5(Vertx vertx) {
    vertx.setPeriodic(1000, id -> {
      // This handler will get called every second
      System.out.println("timer fired!");
    });
  }

  public void example6(HttpServer server) {
    // Respond to each http request with "Hello World"
    server.requestHandler(request -> {
      // This handler will be called every time an HTTP request is received at the server
      request.response().end("hello world!");
    });
  }

  public void example7(Vertx vertx) {
    vertx.executeBlocking(() -> {
      // Call some blocking API that takes a significant amount of time to return
      return someAPI.blockingMethod("hello");
    }).onComplete(res -> {
      System.out.println("The result is: " + res.result());
    });
  }

  public void workerExecutor1(Vertx vertx) {
    WorkerExecutor executor = vertx.createSharedWorkerExecutor("my-worker-pool");
    executor.executeBlocking(() -> {
      // Call some blocking API that takes a significant amount of time to return
      return someAPI.blockingMethod("hello");
    }).onComplete(res -> {
      System.out.println("The result is: " + res.result());
    });
  }

  public void workerExecutor2(WorkerExecutor executor) {
    executor.close();
  }

  public void workerExecutor3(Vertx vertx) {
    //
    // 10 threads max
    int poolSize = 10;

    // 2 minutes
    long maxExecuteTime = 2;
    TimeUnit maxExecuteTimeUnit = TimeUnit.MINUTES;

    WorkerExecutor executor = vertx.createSharedWorkerExecutor("my-worker-pool", poolSize, maxExecuteTime, maxExecuteTimeUnit);
  }

  BlockingAPI someAPI = new BlockingAPI();

  class BlockingAPI {
    String blockingMethod(String str) {
      return str;
    }
  }

  public void vertxBuilder(VertxOptions options, VertxMetricsFactory metricsFactory, VertxTracerFactory tracerFactory) {
    Vertx vertx = Vertx.builder()
      .with(options)
      .withTracer(tracerFactory)
      .withMetrics(metricsFactory)
      .build();
  }

  public void clusteredVertxBuilder(VertxOptions options, ClusterManager clusterManager) {
    Future<Vertx> vertx = Vertx.builder()
      .with(options)
      .withClusterManager(clusterManager)
      .buildClustered();
  }

  public void exampleFuture1(Vertx vertx, Handler<HttpServerRequest> requestHandler) {
    FileSystem fs = vertx.fileSystem();

    Future<FileProps> future = fs.props("/my_file.txt");

    future.onComplete((AsyncResult<FileProps> ar) -> {
      if (ar.succeeded()) {
        FileProps props = ar.result();
        System.out.println("File size = " + props.size());
      } else {
        System.out.println("Failure: " + ar.cause().getMessage());
      }
    });
  }

  public void promiseCallbackOrder(Future<Void> future) {
    future.onComplete(ar -> {
      // Do something
    });
    future.onComplete(ar -> {
      // May be invoked first
    });
  }

  private void legacyGreetAsync(Handler<AsyncResult<String>> promise) {
  }

  public void exampleFutureComposition1(Vertx vertx) {

    FileSystem fs = vertx.fileSystem();

    Future<Void> future = fs
      .createFile("/foo")
      .compose(v -> {
        // When the file is created (fut1), execute this:
        return fs.writeFile("/foo", Buffer.buffer());
      })
      .compose(v -> {
        // When the file is written (fut2), execute this:
        return fs.move("/foo", "/bar");
      });
  }

  public void exampleFuture2(Vertx vertx, Handler<HttpServerRequest> requestHandler) {
    FileSystem fs = vertx.fileSystem();

    Future<FileProps> future = fs.props("/my_file.txt");

    future.onComplete((AsyncResult<FileProps> ar) -> {
      if (ar.succeeded()) {
        FileProps props = ar.result();
        System.out.println("File size = " + props.size());
      } else {
        System.out.println("Failure: " + ar.cause().getMessage());
      }
    });
  }

  public void exampleFutureAll1(HttpServer httpServer, NetServer netServer) {
    Future<HttpServer> httpServerFuture = httpServer.listen();

    Future<NetServer> netServerFuture = netServer.listen();

    Future.all(httpServerFuture, netServerFuture).onComplete(ar -> {
      if (ar.succeeded()) {
        // All servers started
      } else {
        // At least one server failed
      }
    });
  }

  public void exampleFutureAll2(Future<?> future1, Future<?> future2, Future<?> future3) {
    Future.all(Arrays.asList(future1, future2, future3));
  }

  public void exampleFutureAny1(Future<String> future1, Future<String> future2) {
    Future.any(future1, future2).onComplete(ar -> {
      if (ar.succeeded()) {
        // At least one is succeeded
      } else {
        // All failed
      }
    });
  }

  public void exampleFutureAny2(Future<?> f1, Future<?> f2, Future<?> f3) {
    Future.any(Arrays.asList(f1, f2, f3));
  }

  public void exampleFutureJoin1(Future<?> future1, Future<?> future2, Future<?> future3) {
    Future.join(future1, future2, future3).onComplete(ar -> {
      if (ar.succeeded()) {
        // All succeeded
      } else {
        // All completed and at least one failed
      }
    });
  }

  public void exampleFutureJoin2(Future<?> future1, Future<?> future2, Future<?> future3) {
    Future.join(Arrays.asList(future1, future2, future3));
  }

  class MyOrderProcessorVerticle extends VerticleBase {

  }

  public void simplestVerticle() {
    class MyVerticle extends VerticleBase {

      // Called when verticle is deployed
      public Future<?> start() throws Exception {
        return super.start();
      }

      // Optional - called when verticle is un-deployed
      public Future<?> stop() throws Exception {
        return super.stop();
      }
    }
  }

  public void httpServerVerticle() {
    class MyVerticle extends VerticleBase {

      private HttpServer server;

      @Override
      public Future<?> start() {
        server = vertx.createHttpServer().requestHandler(req -> {
          req.response()
            .putHeader("content-type", "text/plain")
            .end("Hello from Vert.x!");
        });

        // Now bind the server:
        return server.listen(8080);
      }
    }
  }

  public void oneLinerVerticle(Vertx vertx) {
    Deployable verticle = context -> vertx
      .createHttpServer()
      .requestHandler(req -> req.response()
        .putHeader("content-type", "text/plain")
        .end("Hello from Vert.x!"))
      .listen(8080);
  }

  public void verticleContract() {

    class MyVerticle extends AbstractVerticle {
      @Override
      public void start(Promise<Void> startPromise) throws Exception {
        Future<String> future = bindService();

        // Requires to write
        future.onComplete(ar -> {
          if (ar.succeeded()) {
            startPromise.complete();
          } else {
            startPromise.fail(ar.cause());
          }
        });

        // Or
        future
          .<Void>mapEmpty()
          .onComplete(startPromise);
      }
    }

  }

  static Future<String> bindService() {
    throw new UnsupportedOperationException();
  }


  public void example7_1(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions().setThreadingModel(ThreadingModel.WORKER);
    vertx.deployVerticle(new MyOrderProcessorVerticle(), options);
  }

  public void example7_2(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD);
    vertx.deployVerticle(new MyOrderProcessorVerticle(), options);
  }

  public void example8(Vertx vertx) {

    VerticleBase myVerticle = new MyVerticle();
    vertx.deployVerticle(myVerticle);
  }

  static class MyVerticle extends VerticleBase {
  }

  public void example9(Vertx vertx) {

    // Deploy a Java verticle - the name is the fully qualified class name of the verticle class
    vertx.deployVerticle("com.mycompany.MyOrderProcessorVerticle");

  }

  public void example10(Vertx vertx) {
    vertx
      .deployVerticle(new MyOrderProcessorVerticle())
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Deployment id is: " + res.result());
        } else {
          System.out.println("Deployment failed!");
        }
      });
  }

  public void example11(Vertx vertx, String deploymentID) {
    vertx
      .undeploy(deploymentID)
      .onComplete(res -> {
        if (res.succeeded()) {
          System.out.println("Undeployed ok");
        } else {
          System.out.println("Undeploy failed!");
        }
      });
  }

  public void example12(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions().setInstances(16);
    vertx.deployVerticle(() -> new MyOrderProcessorVerticle(), options);
  }


  public void example13(Vertx vertx) {
    JsonObject config = new JsonObject().put("name", "tim").put("directory", "/blah");
    DeploymentOptions options = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle(new MyOrderProcessorVerticle(), options);
  }

  public void example15(Vertx vertx) {
    long timerID = vertx.setTimer(1000, id -> {
      System.out.println("And one second later this is printed");
    });

    System.out.println("First this is printed");
  }

  public void example16(Vertx vertx) {
    long timerID = vertx.setPeriodic(1000, id -> {
      System.out.println("And every second this is printed");
    });

    System.out.println("First this is printed");
  }

  public void example17(Vertx vertx, long timerID) {
    vertx.cancelTimer(timerID);
  }

  public void timerExample(Vertx vertx) {
    // Create a timer
    Future<String> timer = vertx
      .timer(10, TimeUnit.SECONDS)
      .map(v -> "Success");

    timer.onSuccess(value -> {
      System.out.println("Timer fired: " + value);
    });
    timer.onFailure(cause -> {
      System.out.println("Timer cancelled: " + cause.getMessage());
    });
  }

  public void example18(String className, Exception exception) {

    // Note -these classes are Java only

    // You would normally maintain one static instance of Logger per Java class:

    Logger logger = LoggerFactory.getLogger(className);

    logger.info("something happened");
    logger.error("oops!", exception);
  }

  public void retrieveContext(Vertx vertx) {
    Context context = vertx.getOrCreateContext();
  }

  public void retrieveContextType(Vertx vertx) {
    Context context = vertx.getOrCreateContext();
    if (context.isEventLoopContext()) {
      System.out.println("Context attached to Event Loop");
    } else if (context.isWorkerContext()) {
      System.out.println("Context attached to Worker Thread");
    } else if (! Context.isOnVertxThread()) {
      System.out.println("Context not attached to a thread managed by vert.x");
    }
  }

  public void runInContext(Vertx vertx) {
    vertx.getOrCreateContext().runOnContext( (v) -> {
      System.out.println("This will be executed asynchronously in the same context");
    });
  }

  public void runInContextWithData(Vertx vertx) {
    final Context context = vertx.getOrCreateContext();
    context.put("data", "hello");
    context.runOnContext((v) -> {
      String hello = context.get("data");
    });
  }

  public void systemAndEnvProperties() {
    System.getProperty("prop");
    System.getenv("HOME");
  }

  public void configureDNSServers() {
    Vertx vertx = Vertx.vertx(new VertxOptions().
        setAddressResolverOptions(
            new AddressResolverOptions().
                addServer("192.168.0.1").
                addServer("192.168.0.2:40000"))
    );
  }

  public void configureHosts() {
    Vertx vertx = Vertx.vertx(new VertxOptions().
        setAddressResolverOptions(
            new AddressResolverOptions().
                setHostsPath("/path/to/hosts"))
    );
  }

  public void configureSearchDomains() {
    Vertx vertx = Vertx.vertx(new VertxOptions().
        setAddressResolverOptions(
            new AddressResolverOptions().addSearchDomain("foo.com").addSearchDomain("bar.com"))
    );
  }

  public void deployVerticleWithDifferentWorkerPool(Vertx vertx) {
    vertx.deployVerticle(new MyOrderProcessorVerticle(), new DeploymentOptions().setWorkerPoolName("the-specific-pool"));
  }

  public void configureNative() {
    Vertx vertx = Vertx.vertx(new VertxOptions().
      setPreferNativeTransport(true)
    );

    // True when native is available
    boolean usingNative = vertx.isNativeTransportEnabled();
    System.out.println("Running with native: " + usingNative);
  }

  public void configureTransport() {

    // Use epoll/kqueue/io_uring native transport depending on OS
    Transport transport = Transport.nativeTransport();

    // Or use a very specific transport
    transport = Transport.EPOLL;

    Vertx vertx = Vertx.builder()
      .withTransport(transport)
      .build();
  }

  public void configureLinuxOptions(Vertx vertx, boolean fastOpen, boolean cork, boolean quickAck, boolean reusePort) {
    // Available on Linux
    vertx.createHttpServer(new HttpServerOptions()
      .setTcpFastOpen(fastOpen)
      .setTcpCork(cork)
      .setTcpQuickAck(quickAck)
      .setReusePort(reusePort)
    );
  }

  public void configureBSDOptions(Vertx vertx, boolean reusePort) {
    // Available on BSD
    vertx.createHttpServer(new HttpServerOptions().setReusePort(reusePort));
  }

  public void tcpServerWithDomainSockets(Vertx vertx) {
    NetServer netServer = vertx.createNetServer();

    // Only available when running on JDK16+, or using a native transport
    SocketAddress address = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    netServer
      .connectHandler(so -> {
      // Handle application
      })
      .listen(address)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // Bound to socket
        } else {
          // Handle failure
        }
      });
  }

  public void httpServerWithDomainSockets(Vertx vertx) {
    HttpServer httpServer = vertx.createHttpServer();

    // Only available when running on JDK16+, or using a native transport
    SocketAddress address = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    httpServer
      .requestHandler(req -> {
        // Handle application
      })
      .listen(address)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // Bound to socket
        } else {
          // Handle failure
        }
      });
  }

  public void tcpClientWithDomainSockets(Vertx vertx) {
    NetClient netClient = vertx.createNetClient();

    // Only available when running on JDK16+, or using a native transport
    SocketAddress addr = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    // Connect to the server
    netClient
      .connect(addr)
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // Connected
        } else {
          // Handle failure
        }
      });
  }

  public void httpClientWithDomainSockets(Vertx vertx) {
    HttpClient httpClient = vertx.createHttpClient();

    // Only available when running on JDK16+, or using a native transport
    SocketAddress addr = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    // Send request to the server
    httpClient.request(new RequestOptions()
      .setServer(addr)
      .setHost("localhost")
      .setPort(8080)
      .setURI("/"))
      .compose(request -> request.send().compose(HttpClientResponse::body))
      .onComplete(ar -> {
        if (ar.succeeded()) {
          // Process response
        } else {
          // Handle failure
        }
      });
  }
}
