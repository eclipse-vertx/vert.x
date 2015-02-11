/*
 * Copyright 2014 Red Hat, Inc.
 *
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  and Apache License v2.0 which accompanies this distribution.
 *
 *  The Eclipse Public License is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 *
 *  The Apache License v2.0 is available at
 *  http://www.opensource.org/licenses/apache2.0.php
 *
 *  You may elect to redistribute this code under either of these licenses.
 */

package examples;

import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;

import java.util.Arrays;

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
    request.response().putHeader("Content-Type", "text/plain").write("some text").end();
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
    vertx.executeBlocking(future -> {
      // Call some blocking API that takes a significant amount of time to return
      String result = someAPI.blockingMethod("hello");
      future.complete(result);
    }, res -> {
      System.out.println("The result is: " + res.result());
    });
  }

  BlockingAPI someAPI = new BlockingAPI();

  class BlockingAPI {
    String blockingMethod(String str) {
      return str;
    }
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

  public void example14(Vertx vertx) {
    DeploymentOptions options = new DeploymentOptions().setIsolationGroup("mygroup");
    options.setExtraClasspath(Arrays.asList("lib/jars/some-library.jar"));
    vertx.deployVerticle("com.mycompany.MyIsolatedVerticle", options);
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





}
