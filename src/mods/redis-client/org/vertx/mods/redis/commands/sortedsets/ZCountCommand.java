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
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * ZCountCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class ZCountCommand extends Command {

	public static final String COMMAND = "zcount";

	public ZCountCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");
		
		Object min = message.body.getField("min");
		checkNull(min, "min can not be null");
		checkType(min, "min must be of type string or double", new Class [] {Double.class, String.class});
		
		Object max = message.body.getField("max");
		checkNull(max, "max can not be null");
		checkType(max, "max must be of type string or double", new Class [] {Double.class, String.class});
		
		try {
			
			Long response = null;
			
			if (min instanceof String && max instanceof String){
				response = context.getClient().zcount(key, (String)min, (String)max);
			} else if (min instanceof Double && max instanceof Double) {
				response = context.getClient().zcount(key, (Double)min, (Double)max);
			} else {
				throw new CommandException("min and max must be of the same type");
			}
			

			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
