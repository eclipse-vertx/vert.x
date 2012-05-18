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


import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * ZRemCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class ZRemCommand extends Command {

	public static final String COMMAND = "zrem";

	public ZRemCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");
		
		
		JsonArray members = message.body.getArray("members");
		checkNull(members, "members can not be null");
		
		try {
			
			List<String> membervalues = new ArrayList<String>();
			
			
			Iterator<Object> values = members.iterator();
			while (values.hasNext()) {
				Object temp = values.next();
				if (!(temp instanceof String)) {
					throw new CommandException("only string values are allowed");
				}
				membervalues.add((String) temp);
			}	
			
			Long response = context.getClient().zrem(key, membervalues.toArray(new String[membervalues.size()]));
			

			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
