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

package vertx.tests;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.testframework.TestClientBase;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class TestClient extends TestClientBase {

  private EventBus eb;

  @Override
  public void start() {
    super.start();
    tu.appReady();
    eb = vertx.eventBus();
  }

  @Override
  public void stop() {
    super.stop();
  }

  public void testDeployVerticle() {
    final Thread t = Thread.currentThread();
    eb.registerHandler("test-handler", new Handler<Message<String>>() {
      public void handle(Message<String> message) {
        tu.azzert(Thread.currentThread() == t);
        if ("started".equals(message.body)) {
          eb.unregisterHandler("test-handler", this);
          tu.testComplete();
        }
      }
    });

    container.deployVerticle(ChildVerticle.class.getName(), null, 1, new Handler<String>() {
      public void handle(String deployID) {
        tu.azzert(Thread.currentThread() == t);
        tu.azzert(deployID != null);
      }
    });
  }

  public void testUndeployVerticle() {
    final Thread t = Thread.currentThread();
    container.deployVerticle(ChildVerticle.class.getName(), null, 1,
      new Handler<String>() {
        public void handle(final String deploymentID) {
          tu.azzert(Thread.currentThread() == t);
          // Give it at least a second - especially for CI on Amazon
          vertx.setTimer(1000, new Handler<Long>() {
            public void handle(Long tid) {
              eb.registerHandler("test-handler", new Handler<Message<String>>() {
                public void handle(Message<String> message) {
                  if ("stopped".equals(message.body)) {
                    eb.unregisterHandler("test-handler", this);
                    tu.testComplete();
                  }
                }
              });
              container.undeployVerticle(deploymentID, new Handler<Void>() {
                public void handle(Void v) {
                  tu.azzert(Thread.currentThread() == t);
                }
              });
            }
          });
        }
      });
  }

  public void testDeployModule() {
    final Thread t = Thread.currentThread();
    eb.registerHandler("test-handler", new Handler<Message<String>>() {
      public void handle(Message<String> message) {
        tu.azzert(Thread.currentThread() == t);
        if ("started".equals(message.body)) {
          eb.unregisterHandler("test-handler", this);
          tu.testComplete();
        }
      }
    });

    container.deployModule("testmod-deploy1", null, 1, new Handler<String>() {
      public void handle(String deployID) {
        tu.azzert(Thread.currentThread() == t);
        tu.azzert(deployID != null);
      }
    });
  }

  public void testUndeployModule() {
    final Thread t = Thread.currentThread();
    container.deployModule("testmod-deploy1", null, 1,
        new Handler<String>() {
          public void handle(final String deploymentID) {
            tu.azzert(Thread.currentThread() == t);
            // Give it at least a second - especially for CI on Amazon
            vertx.setTimer(1000, new Handler<Long>() {
              public void handle(Long tid) {
                eb.registerHandler("test-handler", new Handler<Message<String>>() {
                  public void handle(Message<String> message) {
                    if ("stopped".equals(message.body)) {
                      eb.unregisterHandler("test-handler", this);
                      tu.testComplete();
                    }
                  }
                });
                container.undeployModule(deploymentID, new Handler<Void>() {
                  public void handle(Void v) {
                    tu.azzert(Thread.currentThread() == t);
                  }
                });
              }
            });
          }
        });
  }

  public void testDeployNestedModule() {
    final Thread t = Thread.currentThread();
    eb.registerHandler("test-handler", new Handler<Message<String>>() {
      public void handle(Message<String> message) {
        tu.azzert(Thread.currentThread() == t);
        if ("started".equals(message.body)) {
          eb.unregisterHandler("test-handler", this);
          tu.testComplete();
        }
      }
    });

    container.deployModule("testmod-deploy2", null, 1, new Handler<String>() {
      public void handle(String deployID) {
        tu.azzert(Thread.currentThread() == t);
        tu.azzert(deployID != null);
      }
    });
  }
}

