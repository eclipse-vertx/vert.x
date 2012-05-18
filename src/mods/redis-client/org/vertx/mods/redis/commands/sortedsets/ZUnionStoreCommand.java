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
package org.vertx.mods.redis.commands.sortedsets;


import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.ZParams;
import redis.clients.jedis.ZParams.Aggregate;
import redis.clients.jedis.exceptions.JedisException;

/**
 * ZUnionStoreCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class ZUnionStoreCommand extends Command {

	public static final String COMMAND = "zunionstore";

	public ZUnionStoreCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		JsonArray keys = message.body.getArray("keys");
		checkNull(keys, "keys can not be null");

		String destination = getMandatoryString("destination", message);
		checkNull(destination, "destination can not be null");
		
		String aggregate = getMandatoryString("aggregate", message);
		if (aggregate == null || aggregate.equals("")) {
			aggregate = "sum";
		}
		
		
		
		try {
			
			
			ZParams params = new ZParams();
			switch (aggregate.toLowerCase()) {
				case "sum":
					params.aggregate(Aggregate.SUM);
					break;
				case "min":
					params.aggregate(Aggregate.MIN);
					break;
				case "max":
					params.aggregate(Aggregate.MAX);
					break;
				default: 
					params.aggregate(Aggregate.SUM);
			}
			
			Long response = context.getClient().zunionstore(destination, params, getStringArray(keys));

			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
