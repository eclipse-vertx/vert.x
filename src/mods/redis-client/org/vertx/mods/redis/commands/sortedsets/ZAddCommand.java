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

import java.util.HashMap;
import java.util.Map;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * ZAddCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class ZAddCommand extends Command {

	public static final String COMMAND = "zadd";

	public ZAddCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");
		
		JsonObject members = message.body.getObject("members");
		
		checkNull(members, "members can not be null");
		
		try {
			
			Map<Double, String> fieldvalues = new HashMap<Double, String>();
			
			for (String fn : members.getFieldNames()) {
				Object fv = members.getField(fn);

				if (!(fv instanceof Double)) {
					throw new CommandException("only doubles are allowed for score value");
				}
				fieldvalues.put((Double)fv, fn);
			}
			
			
			Long response = context.getClient().zadd(key, fieldvalues);
			
			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
