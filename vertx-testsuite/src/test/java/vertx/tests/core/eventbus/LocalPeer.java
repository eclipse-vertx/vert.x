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

package vertx.tests.core.eventbus;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.AsyncResultHandler;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.testframework.TestUtils;

import java.util.Set;
import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalPeer extends EventBusAppBase {

  @Override
  public void start(final Future<Void> startedResult) {
    super.start(startedResult);
  }

  @Override
  public void stop() {
    super.stop();
  }

  protected boolean isLocal() {
    return true;
  }

  public void testPubSubInitialise() {
    final String address = "some-address";
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          public void handle(Message<Buffer> msg) {
            tu.checkThread();
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body()));
            eb.unregisterHandler("some-address", this, new AsyncResultHandler<Void>() {
              public void handle(AsyncResult<Void> event) {
                if (event.succeeded()) {
                  tu.testComplete();
                } else {
                  tu.azzert(false, "Failed to unregister");
                }
              }
            });
          }
        }, new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testPubSubMultipleHandlersInitialise() {

    final String address2 = "some-other-address";
    final Handler<Message<Buffer>> otherHandler = new Handler<Message<Buffer>>() {
      public void handle(Message msg) {
        tu.azzert(false, "Should not receive message");
      }
    };
    eb.registerHandler(address2, otherHandler);

    final String address = "some-address";
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          public void handle(Message<Buffer> msg) {
            tu.checkThread();
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body()));
            eb.unregisterHandler(address, this, new AsyncResultHandler<Void>() {
              public void handle(AsyncResult<Void> event) {
                if (event.succeeded()) {
                  tu.testComplete();
                } else {
                  tu.azzert(false, "Failed to unregister");
                }
              }
            });
            eb.unregisterHandler(address, otherHandler);
          }
        }, new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testPointToPointInitialise() {
    final String address = UUID.randomUUID().toString();
    vertx.sharedData().getSet("addresses").add(address);
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;
          public void handle(Message<Buffer> msg) {
            tu.checkThread();
            tu.azzert(!handled);
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body()));
            eb.unregisterHandler(address, this, new AsyncResultHandler<Void>() {
              public void handle(AsyncResult<Void> event) {
                if (event.succeeded()) {
                  tu.testComplete();
                } else {
                  tu.azzert(false, "Failed to unregister");
                }
              }
            });
            handled = true;
          }
        }, new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testPointToPointRoundRobinInitialise() {
    final String address = "some-address";
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          int count;
          public void handle(Message<Buffer> msg) {
            tu.checkThread();
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body()));
            count++;
            if (count == 2) {
              final Handler<Message<Buffer>> hndlr = this;
              //Finish on a timer to allow any more messages to arrive
              vertx.setTimer(200, new Handler<Long>() {
                public void handle(Long id) {
                  eb.unregisterHandler("some-address", hndlr, new AsyncResultHandler<Void>() {
                    public void handle(AsyncResult<Void> event) {
                      if (event.succeeded()) {
                        tu.testComplete();
                      } else {
                        tu.azzert(false, "Failed to unregister");
                      }
                    }
                  });
                }
              });

            } else if (count > 2) {
              tu.azzert(false, "Too many messages");
            }
          }
        }, new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );

  }

  public void testReplyInitialise() {
    final String address = UUID.randomUUID().toString();
    Set<String> addresses = vertx.sharedData().getSet("addresses");
    addresses.add(address);
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;

          public void handle(Message<Buffer> msg) {
            tu.checkThread();
            tu.azzert(!handled);
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body()));
            eb.unregisterHandler(address, this);
            handled = true;
            msg.reply(new Buffer("reply" + address));
          }
        }, new AsyncResultHandler<Void>() {
      public void handle(AsyncResult<Void> event) {
        if (event.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    }
    );
  }

  public void testReplyDifferentTypeInitialise() {
    final String address = UUID.randomUUID().toString();
    Set<String> addresses = vertx.sharedData().getSet("addresses");
    addresses.add(address);
    eb.registerHandler(address, new Handler<Message<Buffer>>() {
          boolean handled = false;

          public void handle(Message<Buffer> msg) {
            tu.checkThread();
            tu.azzert(!handled);
            tu.azzert(TestUtils.buffersEqual((Buffer) data.get("buffer"), msg.body()));
            eb.unregisterHandler(address, this);
            handled = true;
            msg.reply("reply" + address);
          }
        }, new AsyncResultHandler<Void>() {
          public void handle(AsyncResult<Void> event) {
            if (event.succeeded()) {
              tu.testComplete();
            } else {
              tu.azzert(false, "Failed to register");
            }
          }
        }
    );
  }

  public void testReplyUntypedHandlerInitialise() {
    testReplyDifferentTypeInitialise();
  }


}
