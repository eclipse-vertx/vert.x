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

import static vertx.tests.core.eventbus.LocalEchoClient.TIMEOUT;

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

  public void testEchoJsonArrayInitialise() {
    echoInitialise();
  }

  public void testEchoNullJsonInitialise() {
    echoInitialise();
  }

  public void testEchoNullJsonArrayInitialise() {
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
    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        // Reply *after* the timeout
        vertx.setTimer(TIMEOUT * 2, new Handler<Long>() {
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

  public void testSendReplyWithTimeoutNoReplyHandlerInitialise() {

    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        // Reply *after* the timeout
        message.replyWithTimeout("bar", TIMEOUT, new Handler<AsyncResult<Message<String>>>() {
          @Override
          public void handle(AsyncResult<Message<String>> event) {
            tu.azzert(event.failed(), "Should not get a reply after timeout");
            tu.azzert(event.cause() instanceof ReplyException);
            ReplyException re = (ReplyException)event.cause();
            tu.azzert(ReplyFailure.NO_HANDLERS == re.failureType());
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

  public void testSendWithDefaultTimeoutNoReplyInitialise() {

    eb.setDefaultReplyTimeout(TIMEOUT);
    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        // Reply *after* the timeout
        vertx.setTimer(TIMEOUT * 2, new Handler<Long>() {
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

    String address = "some-address";
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {
        message.replyWithTimeout("bar", TIMEOUT, new Handler<AsyncResult<Message<String>>>() {
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

    String address = "some-address";
    final long start = System.currentTimeMillis();
    eb.registerHandler(address, new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> message) {

        message.replyWithTimeout("bar", TIMEOUT, new Handler<AsyncResult<Message<String>>>() {
          boolean called;
          @Override
          public void handle(AsyncResult<Message<String>> replyReply) {
            tu.azzert(!called);
            tu.azzert(replyReply.failed());
            tu.azzert(System.currentTimeMillis() - start >= TIMEOUT);
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
