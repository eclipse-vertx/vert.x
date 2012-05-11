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

package org.vertx.mods.redis;


import java.util.HashMap;
import java.util.Map;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;
import org.vertx.mods.redis.commands.DecrByCommand;
import org.vertx.mods.redis.commands.DecrCommand;
import org.vertx.mods.redis.commands.DelCommand;
import org.vertx.mods.redis.commands.ExistsCommand;
import org.vertx.mods.redis.commands.GetCommand;
import org.vertx.mods.redis.commands.GetSetCommand;
import org.vertx.mods.redis.commands.IncrByCommand;
import org.vertx.mods.redis.commands.IncrCommand;
import org.vertx.mods.redis.commands.SetCommand;


import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;



/**
 * RedisClient Bus Module
 * <p>
 * Please see the busmods manual for a full description
 * <p>
 * 
 * @author <a href="http://marx-labs.de">Thorsten Marx</a>
 */
public class RedisClient extends BusModBase implements
		Handler<Message<JsonObject>>, CommandContext {
	
	private static final Map<String, Command> commands = new HashMap<String, Command>();
	static {
		commands.put(SetCommand.COMMAND, new SetCommand());
		commands.put(GetCommand.COMMAND, new GetCommand());
		commands.put(ExistsCommand.COMMAND, new ExistsCommand());
		commands.put(DelCommand.COMMAND, new DelCommand());
		commands.put(GetSetCommand.COMMAND, new GetSetCommand());
		
		commands.put(IncrCommand.COMMAND, new IncrCommand());
		commands.put(IncrByCommand.COMMAND, new IncrByCommand());
		commands.put(DecrCommand.COMMAND, new DecrCommand());
		commands.put(DecrByCommand.COMMAND, new DecrByCommand());
	}

	private String address;
	private String host;
	private int port;
	private Jedis redis;

	public void start() {
		super.start();

		address = getOptionalStringConfig("address", "vertx.redis-client");
		host = getOptionalStringConfig("host", "localhost");
		port = getOptionalIntConfig("port", 6379);

		try {
			redis = new Jedis(host, port);
			redis.ping();
			eb.registerHandler(address, this);
		} catch (JedisException e) {
			logger.error("Failed to connect to redis server", e);
		}
	}

	public void stop() {
		redis.quit();
	}

	public void handle(Message<JsonObject> message) {
		String command = message.body.getString("command");

		if (command == null) {
			sendError(message, "action must be specified");
			return;
		}
		
		Command commandHandler = commands.get(command.toLowerCase());
		
		if (commandHandler != null) {
			try {
				commandHandler.handle(message, this);
			} catch (CommandException e) {
				sendError(message, e.getLocalizedMessage());
			}
		} else {
			sendError(message, "Invalid command: " + command);
			return;
		}
	}


	@Override
	public Jedis getClient() {
		return redis;
	}

	
	

}
