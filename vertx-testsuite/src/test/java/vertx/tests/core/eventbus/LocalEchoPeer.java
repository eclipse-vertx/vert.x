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
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import java.util.UUID;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEchoPeer extends EventBusAppBase {

  private static final Logger log = LoggerFactory.getLogger(LocalEchoPeer.class);

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

  public void testEchoStringInitialise() {
    echoInitialise();
  }

  public void testEchoNullStringInitialise() {
    echoInitialise();
  }

  public void testEchoBooleanTrueInitialise() {
    echoInitialise();
  }

  public void testEchoBooleanFalseInitialise() {
    echoInitialise();
  }

  public void testEchoNullBooleanInitialise() {
    echoInitialise();
  }

  public void testEchoBufferInitialise() {
    echoInitialise();
  }

  public void testEchoNullBufferInitialise() {
    echoInitialise();
  }

  public void testEchoByteArrayInitialise() {
    echoInitialise();
  }

  public void testEchoNullByteArrayInitialise() {
    echoInitialise();
  }

  public void testEchoByteInitialise() {
    echoInitialise();
  }

  public void testEchoNullByteInitialise() {
    echoInitialise();
  }

  public void testEchoCharacterInitialise() {
    echoInitialise();
  }

  public void testEchoNullCharacterInitialise() {
    echoInitialise();
  }

  public void testEchoDoubleInitialise() {
    echoInitialise();
  }

  public void testEchoNullDoubleInitialise() {
    echoInitialise();
  }

  public void testEchoFloatInitialise() {
    echoInitialise();
  }

  public void testEchoNullFloatInitialise() {
    echoInitialise();
  }

  public void testEchoIntInitialise() {
    echoInitialise();
  }

  public void testEchoNullIntInitialise() {
    echoInitialise();
  }

  public void testEchoJsonInitialise() {
    echoInitialise();
  }

  public void testEchoNullJsonInitialise() {
    echoInitialise();
  }

  public void testEchoLongInitialise() {
    echoInitialise();
  }

  public void testEchoNullLongInitialise() {
    echoInitialise();
  }

  public void testEchoShortInitialise() {
    echoInitialise();
  }

  public void testEchoNullShortInitialise() {
    echoInitialise();
  }

  public void testSendWithTimeoutReplyInitialise() {
    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(Message<String> message) {
        message.reply("bar");
      }
    }, new Handler<AsyncResult<Void>>() {
          @Override
          public void handle(AsyncResult<Void> res) {
            if (res.succeeded()) {
              tu.testComplete();
            } else {
              tu.azzert(false, "Failed to register");
            }
          }
        });
  }

  public void testSendWithTimeoutNoReplyInitialise() {
    final long timeout = 500;
    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        // Reply *after* the timeout
        vertx.setTimer(timeout * 2, new Handler<Long>() {
          @Override
          public void handle(Long tid) {
            message.reply("bar");
          }
        });
      }
    }, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        if (res.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    });

  }

  public void testSendWithDefaultTimeoutNoReplyInitialise() {
    final long timeout = 500;
    eb.setDefaultReplyTimeout(timeout);
    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        // Reply *after* the timeout
        vertx.setTimer(timeout * 2, new Handler<Long>() {
          @Override
          public void handle(Long tid) {
            message.reply("bar");
          }
        });
      }
    }, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        if (res.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    });

  }

  public void testReplyWithTimeoutReplyInitialise() {
    final long timeout = 500;
    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        message.replyWithTimeout("bar", timeout, new Handler<AsyncResult<Message<String>>>() {
          @Override
          public void handle(AsyncResult<Message<String>> replyReply) {
            tu.azzert(replyReply.succeeded());
            tu.azzert("quux".equals(replyReply.result().body()));
            tu.testComplete();
          }
        });
      }
    }, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        if (res.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    });

  }

  public void testReplyWithTimeoutNoReplyInitialise() {
    final long timeout = 500;
    String address = "some-address";
    final long start = System.currentTimeMillis();
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {

        message.replyWithTimeout("bar", timeout, new Handler<AsyncResult<Message<String>>>() {
          boolean called;
          @Override
          public void handle(AsyncResult<Message<String>> replyReply) {
            tu.azzert(!called);
            tu.azzert(replyReply.failed());
            tu.azzert(System.currentTimeMillis() - start >= timeout);
            tu.testComplete();
            called = true;
          }
        });
      }
    }, new Handler<AsyncResult<Void>>() {
      @Override
      public void handle(AsyncResult<Void> res) {
        if (res.succeeded()) {
          tu.testComplete();
        } else {
          tu.azzert(false, "Failed to register");
        }
      }
    });
  }

  public void testReplyRecipientFailureInitialise() {
    String address = "some-address";
    final String msg = "too many giraffes";
    eb.registerHandler(address, new Handler<Message<String>>() {
          @Override
          public void handle(Message<String> message) {
            message.fail(23, msg);
          }
        }, new Handler<AsyncResult<Void>>() {
          @Override
          public void handle(AsyncResult<Void> res) {
            if (res.succeeded()) {
              tu.testComplete();
            } else {
              tu.azzert(false, "Failed to register");
            }
          }
        });
  }

  public void testReplyRecipientFailureStandardHandlerInitialise() {
    testReplyRecipientFailureInitialise();
  }

  private void echoInitialise() {
    // We generate an address
    final String echoAddress = UUID.randomUUID().toString();
    vertx.sharedData().getMap("echoaddress").put("echoaddress", echoAddress);
    eb.registerHandler(echoAddress, new Handler<Message>() {
          boolean handled = false;
          public void handle(final Message msg) {
            tu.checkThread();
            tu.azzert(!handled);
            eb.unregisterHandler(echoAddress, this, new AsyncResultHandler<Void>() {
              @Override
              public void handle(AsyncResult<Void> event) {
                msg.reply(msg.body());
              }
            });
            handled = true;
          }
        }, new AsyncResultHandler<Void>() {
          public void handle(AsyncResult<Void> event) {
            if (event.succeeded()) {
              tu.testComplete();
            } else {
              event.cause().printStackTrace();
              tu.azzert(false, "Failed to register");
            }
          }
        }
    );
  }


}
