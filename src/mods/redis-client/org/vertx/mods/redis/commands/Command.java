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
}
