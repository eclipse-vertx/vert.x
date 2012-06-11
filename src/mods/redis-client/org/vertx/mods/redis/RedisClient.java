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

import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.vertx.java.busmods.BusModBase;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.commands.Command;
import org.vertx.mods.redis.commands.CommandException;

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
		find("org.vertx.mods.redis.commands.strings");
		find("org.vertx.mods.redis.commands.keys");
		find("org.vertx.mods.redis.commands.hashes");
		find("org.vertx.mods.redis.commands.lists");
		find("org.vertx.mods.redis.commands.sets");
		find("org.vertx.mods.redis.commands.sortedsets");
		find("org.vertx.mods.redis.commands.connection"); 
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
			sendError(message, "command must be specified");
			return;
		}
		
		Command commandHandler = commands.get(command.toLowerCase());

		if (commandHandler != null) {
			try {
				commandHandler.handle(message, this);
			} catch (CommandException e) {
				sendError(message, e.getMessage());
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

	/**
	 * Helper to load all commands from a package
	 * @param pckgname
	 */
	private static void find(String pckgname) {
		// Code from JWhich
		// ======
		// Translate the package name into an absolute path
		String name = new String(pckgname);
		if (!name.startsWith("/")) {
			name = "/" + name;
		}
		name = name.replace('.', '/');

		// Get a File object for the package
		URL url = RedisClient.class.getResource(name);
		File directory = new File(url.getFile());
		// New code
		// ======
		if (directory.exists()) {
			// Get the list of the files contained in the package
			String[] files = directory.list();
			for (int i = 0; i < files.length; i++) {

				// we are only interested in .class files
				if (files[i].endsWith(".class")) {
					// removes the .class extension
					String classname = files[i].substring(0,
							files[i].length() - 6);
					try {
						Class<?> clazz = Class.forName(pckgname + "." + classname);
						// loaded class is a command
						if (Command.class.isAssignableFrom(clazz)) {
							Object o = clazz.newInstance();
							commands.put(((Command) o).getName(), (Command) o);
						}
					} catch (ClassNotFoundException cnfex) {
						System.err.println(cnfex);
					} catch (InstantiationException iex) {
						// We try to instantiate an interface
						// or an object that does not have a
						// default constructor
					} catch (IllegalAccessException iaex) {
						// The class is not public
					}
				}
			}
		}
	}

}
