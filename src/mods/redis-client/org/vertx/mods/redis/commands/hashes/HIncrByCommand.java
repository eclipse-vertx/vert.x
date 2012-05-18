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
package org.vertx.mods.redis.commands.hashes;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * HIncrByCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class HIncrByCommand extends Command {

	public static final String COMMAND = "hincrby";

	public HIncrByCommand () {
		super(COMMAND);
	}
	
	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");
		
		
		String field = getMandatoryString("field", message);
		checkNull(field, "field can not be null");
		
		Number increment = message.body.getNumber("increment");
		checkNull(increment, "increment can not be null");
		checkType(increment, "increment must be of type integer or long", new Class<?> []{Integer.class, Long.class});
		
		try {
			Long value = context.getClient().hincrBy(key, field, increment.longValue());

			response(message, value);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
