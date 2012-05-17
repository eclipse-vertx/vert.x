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
package org.vertx.mods.redis.commands.sets;


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.BinaryClient.LIST_POSITION;
import redis.clients.jedis.exceptions.JedisException;

/**
 * SInterCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class SInterCommand extends Command {

	public static final String COMMAND = "sinter";

	public SInterCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		JsonArray keys = message.body.getArray("keys");
		checkNull(keys, "keys can not be null");
		
		
		try {
			List<String> keyvalues = new ArrayList<String>();
			
			
			Iterator<Object> values = keys.iterator();
			while (values.hasNext()) {
				Object temp = values.next();
				if (!(temp instanceof String)) {
					throw new CommandException("only string values are allowed");
				}
				keyvalues.add((String) temp);
			}
			
			
			Set<String> response_values = context.getClient().sinter(keyvalues.toArray(new String[keyvalues.size()]));
			

			JsonArray keys_json;
			if (response_values != null && !response_values.isEmpty()) {
				keys_json = new JsonArray(response_values.toArray());
			} else {
				 keys_json = new JsonArray();
			}
			response(message, keys_json);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
