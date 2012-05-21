package vertx.tests.busmods.redisclient.commands.sets;

import static org.junit.Assert.*;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.CommandException;
import org.vertx.mods.redis.commands.sets.*;


import redis.clients.jedis.Jedis;
import vertx.tests.busmods.redisclient.commands.CommandTest;

public class SetCommandsTest extends CommandTest {

	static CommandContext context;
	
	@BeforeClass
	public static void before () {
		final Jedis jedis = new Jedis("localhost");
		context = new CommandContext() {
			
			@Override
			public Jedis getClient() {
				return jedis;
			}
		};
	}
	@AfterClass
	public static void after () {
		context.getClient().quit();
	}
	
	
	
}
