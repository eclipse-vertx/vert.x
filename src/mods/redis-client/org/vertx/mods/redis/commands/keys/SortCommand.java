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
package org.vertx.mods.redis.commands.keys;

import java.util.List;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

import redis.clients.jedis.SortingParams;
import redis.clients.jedis.exceptions.JedisException;

/**
 * SortCommand
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class SortCommand extends Command {
	
	public static final String COMMAND = "sort";

	public SortCommand () {
		super(COMMAND);
	}
	
	@Override
	public void handle(Message<JsonObject> message, CommandContext context) throws CommandException {
		String key = getMandatoryString("key", message);
		checkNull(key, "key can not be null");

		String resultkey = getMandatoryString("resultkey", message);
		
		


		try {

			List<String> values = null;
			
			if (resultkey != null) {
				
				handleStore(message, context, key, resultkey);
				return;
			}
			
			SortingParams sorting = getSortingParams(message);
			
			if (sorting != null) {
				values = context.getClient().sort(key, sorting);
			} else {
				values = context.getClient().sort(key);
			}
			
			JsonArray result = null;
			if (values != null && !values.isEmpty()) {
				result = new JsonArray(values.toArray());
			} else {
				result = new JsonArray();
			}

			response(message, result);
			
		} catch (JedisException e) {
			sendError(message, e.getLocalizedMessage());
		}
	}
	
	private SortingParams getSortingParams (Message<JsonObject> message) {
		SortingParams params = new SortingParams();
		boolean hasParams = false;
		
		boolean alpha = message.body.getBoolean("alpha", false);
		if (alpha) {
			params.alpha();
			hasParams = true;
		}
		boolean asc = message.body.getBoolean("asc", false);
		if (asc) {
			params.asc();
			hasParams = true;
		}
		boolean desc = message.body.getBoolean("desc", false);
		if (desc) {
			params.desc();
			hasParams = true;
		}
		String by = message.body.getString("by", null);
		if (by != null) {
			params.by(by);
			hasParams = true;
		}
		Number start = message.body.getNumber("start");
		Number count = message.body.getNumber("count");
		if ((start != null && start instanceof Integer) && (count != null && count instanceof Integer)) {
			params.limit(start.intValue(), count.intValue());
			hasParams = true;
		}
		boolean nosort = message.body.getBoolean("by", false);
		if (nosort) {
			params.nosort();
			hasParams = true;
		}
		
		
		if (!hasParams) {
			return null;
		}
		return params;
	}
	
	private void handleStore (Message<JsonObject> message, CommandContext context, String key, String resultkey) {
		Long storeResult = null;
		SortingParams sorting = getSortingParams(message);
		if (sorting != null) {
			storeResult = context.getClient().sort(key, sorting, resultkey);
		} else {
			storeResult = context.getClient().sort(key, resultkey);
		}
		
		response(message, storeResult);
	}
	
	
}
