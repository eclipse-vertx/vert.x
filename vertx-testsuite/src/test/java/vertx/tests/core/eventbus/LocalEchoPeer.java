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
