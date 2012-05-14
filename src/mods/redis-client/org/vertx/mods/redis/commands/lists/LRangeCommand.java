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
package org.vertx.mods.redis.commands.lists;

import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * LRangeCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class LRangeCommand extends Command {
	
	public static final String COMMAND = "lrange";

	public LRangeCommand () {
		super(COMMAND);
	}
	
	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");		

		Number start = message.body.getNumber("start");
		checkNull(start, "start can not be null");
		checkType(start, "start must be an integer or long", new Class<?> []{Integer.class, Long.class});
		
		
		Number end = message.body.getNumber("end");
		checkNull(end, "end can not be null");
		checkType(end, "end must be an integer or long", new Class<?> []{Integer.class, Long.class});
		
		try {
			List<String> value = context.getClient().lrange(key, start.longValue(), end.longValue());
			
			
			JsonArray response = new JsonArray(new ArrayList<Object>(value));
			
			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
