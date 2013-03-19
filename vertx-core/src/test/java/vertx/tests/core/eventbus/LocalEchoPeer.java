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
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEchoPeer extends EventBusAppBase {

  @Override
  public void start() {
    super.start();
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
    eb.registerHandler(LocalEchoClient.ECHO_ADDRESS, new Handler<Message>() {
          boolean handled = false;

          public void handle(Message msg) {
            tu.checkThread();
            tu.azzert(!handled);
            eb.unregisterHandler(LocalEchoClient.ECHO_ADDRESS, this);
            handled = true;
            msg.reply(msg.body);
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


}
