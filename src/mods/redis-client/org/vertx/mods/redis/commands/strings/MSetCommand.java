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
package org.vertx.mods.redis.commands.strings;

import java.util.ArrayList;
import java.util.List;

import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.exceptions.JedisException;

/**
 * MSetCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class MSetCommand extends Command {

	public static final String COMMAND = "mset";

	public MSetCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		JsonObject keyvalues = message.body.getObject("keyvalues");
		
		checkNull(keyvalues, "keyvalues can not be null");
		
		try {
			
			List<String> keyvalue = new ArrayList<String>();
			
		
			for (String fn : keyvalues.getFieldNames()) {
				Object fv = keyvalues.getField(fn);
				if (!(fv instanceof String)) {
					throw new CommandException("only stringvalues are allowed for field values");
				}
				keyvalue.add(fn);
				keyvalue.add((String) fv);
			}
			
			String response = context.getClient().mset(keyvalue.toArray(new String[keyvalue.size()]));
			
			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
