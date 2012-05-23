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

package vertx.tests.busmods.redisclient;

import org.vertx.java.core.Handler;
import org.vertx.java.core.SimpleHandler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.framework.TestClientBase;

/**
 * 
 * Most of the testing is done in JS since it's so much easier to play with JSON
 * in JS rather than Java
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class TestClient extends TestClientBase {

	private EventBus eb;

	private String persistorID;
	
	public TestClient() {
		vertx = Vertx.newVertx();
	}

	@Override
	public void start() {
		super.start();
		eb = vertx.eventBus();
		JsonObject config = new JsonObject();
		config.putString("address", "test.persistor");

		persistorID = container.deployVerticle("redis-client", config, 1,
				new SimpleHandler() {
					public void handle() {
						tu.appReady();
					}
				});
	}

	@Override
	public void stop() {
		super.stop();
	}

	public void testPersistor() throws Exception {

		// First delete everything
		JsonObject json = new JsonObject().putString("command", "set")
				.putString("key", "name").putString("value", "thorsten");

		eb.send("test.persistor", json, new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> reply) {
				tu.azzert("ok".equals(reply.body.getString("status")));
			}
		});

		json = new JsonObject().putString("command", "exists")
				.putString("key", "name");
		eb.send("test.persistor", json, new Handler<Message<JsonObject>>() {
			public void handle(Message<JsonObject> reply) {
				tu.azzert("true".equals(reply.body.getString("value")));
			}
		});
	}
}
