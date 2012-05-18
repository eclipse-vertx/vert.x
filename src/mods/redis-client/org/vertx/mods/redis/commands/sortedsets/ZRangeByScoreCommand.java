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


import java.util.Set;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * ZRangeByScoreCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class ZRangeByScoreCommand extends Command {

	public static final String COMMAND = "zrangebyscore";

	public ZRangeByScoreCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");
		
		
		Object min = message.body.getObject("min");
		checkNull(min, "min can not be null");
		checkType(min, "min must be of type double or string", new Class[] {Double.class, String.class});
		
		Object max = message.body.getObject("max");
		checkNull(max, "max can not be null");
		checkType(max, "max must be of type double or string", new Class[] {Double.class, String.class});
		
		Number offset = message.body.getNumber("offset");
		if (offset != null) {
			checkType(offset, "offset must be of type integer", new Class[] {Integer.class});
		}
		Number count = message.body.getNumber("count");
		if (count != null) {
			checkType(count, "count must be of type integer", new Class[] {Integer.class});
		}
		
		try {
			
			Set<String> response_values = null; 
			
			if (min instanceof String && max instanceof String) {
				if (count != null && offset != null) {
					response_values = context.getClient().zrangeByScore(key, (String)min, (String)max, offset.intValue(), count.intValue());
				} else {
					response_values = context.getClient().zrangeByScore(key, (String)min, (String)max);
				}
			} else if (min instanceof Double && max instanceof Double) {
				if (count != null && offset != null) {
					response_values = context.getClient().zrangeByScore(key, (Double)min, (Double)max, offset.intValue(), count.intValue());
				} else {
					response_values = context.getClient().zrangeByScore(key, (Double)min, (Double)max);
				}
			} else {
				throw new CommandException("min and max must be of the same type");
			}

			JsonArray response;
			if (response_values != null && !response_values.isEmpty()) {
				response = new JsonArray(response_values.toArray());
			} else {
				 response = new JsonArray();
			}
			response(message, response);
			
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
