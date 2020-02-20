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
import io.vertx.core.file.FileSystem;
import io.vertx.core.http.*;
import io.vertx.core.impl.logging.Logger;
import io.vertx.core.impl.logging.LoggerFactory;
import io.vertx.core.json.JsonObject;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.SocketAddress;

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
    response.write("some text");
    response.end();
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
    vertx.executeBlocking(promise -> {
      // Call some blocking API that takes a significant amount of time to return
      String result = someAPI.blockingMethod("hello");
      promise.complete(result);
    }, res -> {
      System.out.println("The result is: " + res.result());
    });
  }

  public void workerExecutor1(Vertx vertx) {
    WorkerExecutor executor = vertx.createSharedWorkerExecutor("my-worker-pool");
    executor.executeBlocking(promise -> {
      // Call some blocking API that takes a significant amount of time to return
      String result = someAPI.blockingMethod("hello");
      promise.complete(result);
    }, res -> {
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

  public void exampleFutureAll1(HttpServer httpServer, NetServer netServer) {
    Future<HttpServer> httpServerFuture = Future.future(promise -> httpServer.listen(promise));

    Future<NetServer> netServerFuture = Future.future(promise -> netServer.listen(promise));

    CompositeFuture.all(httpServerFuture, netServerFuture).setHandler(ar -> {
      if (ar.succeeded()) {
        // All servers started
      } else {
        // At least one server failed
      }
    });
  }

  public void exampleFutureAll2(Future future1, Future future2, Future future3) {
    CompositeFuture.all(Arrays.asList(future1, future2, future3));
  }

  public void exampleFutureAny1(Future<String> future1, Future<String> future2) {
    CompositeFuture.any(future1, future2).setHandler(ar -> {
      if (ar.succeeded()) {
        // At least one is succeeded
      } else {
        // All failed
      }
    });
  }

  public void exampleFutureAny2(Future f1, Future f2, Future f3) {
    CompositeFuture.any(Arrays.asList(f1, f2, f3));
  }

  public void exampleFutureJoin1(Future future1, Future future2, Future future3) {
    CompositeFuture.join(future1, future2, future3).setHandler(ar -> {
      if (ar.succeeded()) {
        // All succeeded
      } else {
        // All completed and at least one failed
      }
    });
  }

  public void exampleFutureJoin2(Future future1, Future future2, Future future3) {
    CompositeFuture.join(Arrays.asList(future1, future2, future3));
  }

  public void exampleFuture6(Vertx vertx) {

    FileSystem fs = vertx.fileSystem();

    Future<Void> fut1 = Future.future(promise -> fs.createFile("/foo", promise));

    Future<Void> startFuture = fut1
      .compose(v -> {
      // When the file is created (fut1), execute this:
      return Future.<Void>future(promise -> fs.writeFile("/foo", Buffer.buffer(), promise));
    })
      .compose(v -> {
      // When the file is written (fut2), execute this:
      return Future.future(promise -> fs.move("/foo", "/bar", promise));
    });
  }

  public void example7_1(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions().setWorker(true);
    vertx.deployVerticle("com.mycompany.MyOrderProcessorVerticle", options);
  }

  public void example8(Vertx vertx) {

    Verticle myVerticle = new MyVerticle();
    vertx.deployVerticle(myVerticle);
  }

  class MyVerticle extends AbstractVerticle {

    @Override
    public void start() throws Exception {
      super.start();
    }
  }

  public void example9(Vertx vertx) {

    // Deploy a Java verticle - the name is the fully qualified class name of the verticle class
    vertx.deployVerticle("com.mycompany.MyOrderProcessorVerticle");

    // Deploy a JavaScript verticle
    vertx.deployVerticle("verticles/myverticle.js");

    // Deploy a Ruby verticle verticle
    vertx.deployVerticle("verticles/my_verticle.rb");

  }

  public void example10(Vertx vertx) {
    vertx.deployVerticle("com.mycompany.MyOrderProcessorVerticle", res -> {
      if (res.succeeded()) {
        System.out.println("Deployment id is: " + res.result());
      } else {
        System.out.println("Deployment failed!");
      }
    });
  }

  public void example11(Vertx vertx, String deploymentID) {
    vertx.undeploy(deploymentID, res -> {
      if (res.succeeded()) {
        System.out.println("Undeployed ok");
      } else {
        System.out.println("Undeploy failed!");
      }
    });
  }

  public void example12(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions().setInstances(16);
    vertx.deployVerticle("com.mycompany.MyOrderProcessorVerticle", options);
  }


  public void example13(Vertx vertx) {
    JsonObject config = new JsonObject().put("name", "tim").put("directory", "/blah");
    DeploymentOptions options = new DeploymentOptions().setConfig(config);
    vertx.deployVerticle("com.mycompany.MyOrderProcessorVerticle", options);
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
    vertx.deployVerticle("the-verticle", new DeploymentOptions().setWorkerPoolName("the-specific-pool"));
  }

  public void configureNative() {
    Vertx vertx = Vertx.vertx(new VertxOptions().
      setPreferNativeTransport(true)
    );

    // True when native is available
    boolean usingNative = vertx.isNativeTransportEnabled();
    System.out.println("Running with native: " + usingNative);
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
    // Only available on BSD and Linux
    vertx.createNetServer().connectHandler(so -> {
      // Handle application
    }).listen(SocketAddress.domainSocketAddress("/var/tmp/myservice.sock"));
  }

  public void httpServerWithDomainSockets(Vertx vertx) {
    vertx.createHttpServer().requestHandler(req -> {
      // Handle application
    }).listen(SocketAddress.domainSocketAddress("/var/tmp/myservice.sock"), ar -> {
      if (ar.succeeded()) {
        // Bound to socket
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  public void tcpClientWithDomainSockets(Vertx vertx) {
    NetClient netClient = vertx.createNetClient();

    // Only available on BSD and Linux
    SocketAddress addr = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    // Connect to the server
    netClient.connect(addr, ar -> {
      if (ar.succeeded()) {
        // Connected
      } else {
        ar.cause().printStackTrace();
      }
    });
  }

  public void httpClientWithDomainSockets(Vertx vertx) {
    HttpClient httpClient = vertx.createHttpClient();

    // Only available on BSD and Linux
    SocketAddress addr = SocketAddress.domainSocketAddress("/var/tmp/myservice.sock");

    // Send request to the server
    httpClient.request(HttpMethod.GET, addr, 8080, "localhost", "/")
      .setHandler(resp -> {
        // Process response
      }).end();
  }
}
