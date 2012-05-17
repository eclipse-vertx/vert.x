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
 * SMoveCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class SMoveCommand extends Command {

	public static final String COMMAND = "smove";

	public SMoveCommand() {
		super(COMMAND);
	}

	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		String source = getMandatoryString("source", message);
		checkNull(source, "source can not be null");
		
		String destination = getMandatoryString("destination", message);
		checkNull(destination, "destination can not be null");
		
		String member = getMandatoryString("member", message);
		checkNull(member, "member can not be null");
		
		try {
			Long response = context.getClient().smove(source, destination, member);

			response(message, response);
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}

	}
}
