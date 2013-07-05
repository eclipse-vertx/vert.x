package vertx.tests;

/*
 * Copyright 2013 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
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
          container.deployModule("io.vertx~mod-maven-test~1.0.0");
        }
      }
    });
  }

  public void testMavenDownloadWithProxy() {
    container.deployModule("io.vertx~mod-proxy-maven-server~1.0", new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          container.deployModule("io.vertx~mod-maven-test~1.0.0", new AsyncResultHandler<String>() {
            @Override
            public void handle(AsyncResult<String> res) {
            }
          });
        }
      }
    });
  }

  public void testBintrayDownload() {
    container.deployModule("io.vertx~mod-bintray-server~1.0", new AsyncResultHandler<String>() {
      public void handle(AsyncResult<String> res) {
        if (res.succeeded()) {
          container.deployModule("purplefox~mod-bintray-test~1.0.0");
        }
      }
    });
  }

}
