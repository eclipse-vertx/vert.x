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

package org.vertx.java.deploy.impl.cli;

import static org.vertx.java.deploy.impl.cli.StarterArgs.displaySyntax;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.impl.DefaultVertx;
import org.vertx.java.core.impl.VertxInternal;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.deploy.impl.VerticleManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class Starter {

  private static final Logger log = LoggerFactory.getLogger(Starter.class);

  private static final String VERSION = "vert.x-1.2.3.final";

  public static void main(String[] args) {
    try {
      new Starter(new StarterArgs(args));
    }
    catch (StarterArgs.Problem problem) {
      log.error(problem.getMessage());
      displaySyntax();
    }
  }

  private Starter(StarterArgs args) {
    switch (args.getCommand()) {
      case "version":
        log.info(VERSION);
        break;
      case "run":
        runVerticle(args);
        break;
      case "runmod":
        runModule(args);
        break;
      case "install":
        installModule(args);
        break;
      case "uninstall":
        uninstallModule(args);
        break;
      default:
        displaySyntax();
    }
  }

  private void installModule(StarterArgs args) {
    final CountDownLatch latch = new CountDownLatch(1);
    createVerticleManagerUnclustered(args).installMod(args.getOperand(), new Handler<Boolean>() {
      public void handle(Boolean res) {
        latch.countDown();
      }
    });
    while (true) {
      try {
        latch.await(30, TimeUnit.SECONDS);
        break;
      } catch (InterruptedException e) {
      }
    }
  }

  private void uninstallModule(StarterArgs args) {
    createVerticleManagerUnclustered().uninstallMod(args.getOperand());
  }

  private void runVerticle(StarterArgs args) {
    final VerticleManager mgr = createVerticleManager(args);

    mgr.deployVerticle(
      args.isWorker(),
      args.getOperand(),
      args.getConf(),
      args.getClasspathURLs(),
      args.getInstances(),
      null,
      doneHandler(mgr));

    addShutdownHook(mgr);
    mgr.block();
  }

  private void runModule(StarterArgs args) {
    final VerticleManager mgr = createVerticleManager(args);

    mgr.deployMod(
      args.getOperand(),
      args.getConf(),
      args.getInstances(),
      null,
      doneHandler(mgr));

    addShutdownHook(mgr);
    mgr.block();
  }

  private Handler<String> doneHandler(final VerticleManager mgr) {
    return new Handler<String>() {
      public void handle(String id) {
        if (id == null) {
          // Failed to deploy
          mgr.unblock();
        }
      }
    };
  }

  private VerticleManager createVerticleManager(StarterArgs args) {
    VertxInternal vertx = createVertx(args);
    String repo = args.getRepo();

    return new VerticleManager(vertx, repo);
  }

  private VerticleManager createVerticleManagerUnclustered() {
    return new VerticleManager(createVertxUnclustered());
  }

  private VerticleManager createVerticleManagerUnclustered(StarterArgs args) {
    VertxInternal vertx = createVertxUnclustered();
    String repo = args.getRepo();

    return new VerticleManager(vertx, repo);
  }

  private VertxInternal createVertx(StarterArgs args) {
    return args.isClustered() ? createVertxClustered(args) : createVertxUnclustered();
  }

  private DefaultVertx createVertxClustered(StarterArgs args) {
    return new DefaultVertx(args.getClusterPort(), args.getClusterHost());
  }

  private VertxInternal createVertxUnclustered() {
    return new DefaultVertx();
  }


  private void addShutdownHook(final VerticleManager mgr) {
    Runtime.getRuntime().addShutdownHook(new Thread() {
      public void run() {
        final CountDownLatch latch = new CountDownLatch(1);
        mgr.undeployAll(new SimpleHandler() {
          public void handle() {
            latch.countDown();
          }
        });
        while (true) {
          try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
              log.error("Timed out waiting to undeploy");
            }
            break;
          } catch (InterruptedException e) {
            //OK - can get spurious interupts
          }
        }
      }
    });
  }
}
