/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package vertx.tests;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Handler;
import org.vertx.java.testframework.TestClientBase;

public class RepoDownloadTestClient extends TestClientBase {

  @Override
  public void start() {
    super.start();
    tu.appReady();
  }

  public void testMavenDownload() {
    container.deployModule("io.vertx~mod-maven-server~1.0", new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          // Deploy after a delay to give server time to listen
          vertx.setTimer(2000, new Handler<Long>() {
            @Override
            public void handle(Long event) {
              container.deployModule("io.vertx~mod-maven-test~1.0.0");
            }
          });
        } else {
          res.cause().printStackTrace();
        }
      }
    });
  }

  public void testMavenDownloadWithProxy() {
    container.deployModule("io.vertx~mod-proxy-maven-server~1.0", new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          // this should not use the same module as the regular test because if the regular test
          // already downloaded this module, it will not actually use the proxy at all...

          // Deploy after a delay to give server time to listen
          vertx.setTimer(2000, new Handler<Long>() {
            @Override
            public void handle(Long event) {
              container.deployModule("io.vertx~mod-maven-proxy-test~1.0.0");
            }
          });
        } else {
          res.cause().printStackTrace();
        }
      }
    });
  }

  public void testBintrayDownload() {
    container.deployModule("io.vertx~mod-bintray-server~1.0", new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          // Deploy after a delay to give server time to listen
          vertx.setTimer(2000, new Handler<Long>() {
            @Override
            public void handle(Long event) {
              container.deployModule("purplefox~mod-bintray-test~1.0.0");
            }
          });
        } else {
          res.cause().printStackTrace();
        }
      }
    });
  }

}
