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

import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.impl.EventBusImpl;
import org.vertx.java.core.shareddata.SharedData;
import org.vertx.java.framework.TestClientBase;
import org.vertx.java.tests.core.eventbus.Counter;

import java.util.Map;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public abstract class EventBusAppBase extends TestClientBase {

  protected Map<String, Object> data;
  protected EventBusImpl eb;

  @Override
  public void start() {
    super.start();

    data = SharedData.instance.getMap("data");

    if (isLocal()) {
      eb = (EventBusImpl)EventBus.instance;
    } else {
      int port = Counter.portCounter.getAndIncrement();
      eb = new EventBusImpl(port, "localhost");
    }

    tu.appReady();
  }

  @Override
  public void stop() {
    if (!isLocal()) {
      eb.close(new SimpleHandler() {
        public void handle() {
          EventBusAppBase.super.stop();
        }
      });
    } else {
      super.stop();
    }
  }

  protected abstract boolean isLocal();

}
