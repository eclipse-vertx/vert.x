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

package org.vertx.mods;


import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;



/**
 * RedisClient Bus Module
 * <p>
 * Please see the busmods manual for a full description
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class RedisClient extends BusModBase implements
		Handler<Message<JsonObject>> {

	private String address;
	private String host;
	private int port;
	private Jedis redis;

	public void start() {
		super.start();

		address = getOptionalStringConfig("address", "vertx.redis-client");
		host = getOptionalStringConfig("host", "localhost");
		port = getOptionalIntConfig("port", 6379);

		try {
			redis = new Jedis(host, port);
			redis.ping();
			eb.registerHandler(address, this);
		} catch (JedisException e) {
			logger.error("Failed to connect to redis server", e);
		}
	}

	public void stop() {
		redis.quit();
	}

	public void handle(Message<JsonObject> message) {
		String action = message.body.getString("action");

		if (action == null) {
			sendError(message, "action must be specified");
			return;
		}

		switch (action) {
		case "set":
			doSet(message);
			break;
		case "exists":
			doExists(message);
			break;
		case "get":
			doGet(message);
			break;
		case "del":
			doDel(message);
			break;
		default:
			sendError(message, "Invalid action: " + action);
			return;
		}
	}

	private void doSet(Message<JsonObject> message) {
		String key = getMandatoryString("key", message);
		if (key == null) {
			sendError(message, "key can not be null");
			return;
		}
		
		String value = getMandatoryString("value", message);
		if (value == null) {
			sendError(message, "value can not be null");
			return;
		}
		
		try {
			
			redis.set(key, value);
			sendOK(message);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}
		
	}

	private void doExists(Message<JsonObject> message) {
		String key = getMandatoryString("key", message);
		if (key == null) {
			sendError(message, "key can not be null");
			return;
		}
		
		try {
			boolean exists = redis.exists(key);
			
			JsonObject reply = new JsonObject().putBoolean("exists", exists);
			sendOK(message, reply);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}
	}
	private void doGet(Message<JsonObject> message) {
		String key = getMandatoryString("key", message);
		
		try {
			String value = new String(redis.get(key));
			
			JsonObject reply = new JsonObject().putString("value", value);
			sendOK(message, reply);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}
	}
	private void doDel(Message<JsonObject> message) {
		String key = getMandatoryString("key", message);
		
		try {
			redis.del(key);
			
			sendOK(message);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}
	}

	
	

}
