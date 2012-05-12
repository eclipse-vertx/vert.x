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
package org.vertx.mods.redis.commands.keys;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;

import redis.clients.jedis.exceptions.JedisException;

/**
 * TTLCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class TTLCommand extends Command {
	
	public static final String COMMAND = "ttl";

	public TTLCommand () {
		super(COMMAND);
	}
	
	@Override
	public void handle(Message<JsonObject> message, CommandContext context) {
		String key = getMandatoryString("key", message);
		if (key == null) {
			sendError(message, "key can not be null");
			return;
		}


		try {

			Long value = context.getClient().ttl(key);
			if (value > -1) {
				JsonObject obj = new JsonObject().putNumber("ttl", value);
				sendOK(message, obj);
			} else {
				sendError(message, " key does not exist or does not have a timeout");
			}
			
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
