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

package org.vertx.mods.redis.commands;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;

/**
 * Redis command interface
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public abstract class Command extends BusModBase {
	
	private String name;
	
	public Command (String name) {
		this.name = name;
	}
	
	/**
	 * Handles the command provided in the message
	 * 
	 * @param message The eventbus message
	 */
	public abstract void handle (Message<JsonObject> message, CommandContext context) throws CommandException;
	
	public String getName () {
		return name;
	}
	
	protected void response (Message<JsonObject> message, JsonArray response) {
		JsonObject value = new JsonObject().putArray("value", response);
		sendOK(message, value);
	}
	
	protected void response (Message<JsonObject> message, JsonObject response) {
		JsonObject value = new JsonObject().putObject("value", response);
		sendOK(message, value);
	}
	
	protected void response (Message<JsonObject> message, Number response) {
		JsonObject value = new JsonObject().putNumber("value", response);
		sendOK(message, value);
	}
	
	protected void response (Message<JsonObject> message, String response) {
		JsonObject value = new JsonObject().putString("value", response);
		sendOK(message, value);
	}
	protected void response (Message<JsonObject> message, boolean response) {
		JsonObject value = new JsonObject().putBoolean("value", response);
		sendOK(message, value);
	}
	
//	protected void checkType (Object value, Class<?> type, String error) throws CommandException {
//		if (!type.isInstance(value)) {
//			throw new CommandException(error);
//		}
//	}
	protected void checkType (Object value, String error, Class<?>... types) throws CommandException {
		boolean check = false;
		for (Class<?> type : types) {
			if (type.isInstance(value)) {
				check = true;
				break;
			}
		}
		if (!check) {
			throw new CommandException(error);
		}
	}
	protected void checkNull (Object value, String error) throws CommandException {
		if (value == null) {
			throw new CommandException(error);
		}
	}
	
	protected String[] getStringArray (JsonArray json) throws CommandException {
		String[] stringArray = new String[json.size()];
		for (int i = 0; i < json.size(); i++) {
			Object temp = json.toArray()[i];
			if (!(temp instanceof String)) {
				throw new CommandException("only strings are allowed as value");
			}
			stringArray[i] = (String) temp;
		}
		
		return stringArray;
	}
	
}
