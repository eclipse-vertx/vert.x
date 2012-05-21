package vertx.tests.busmods.redisclient.commands.lists;

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
import org.vertx.mods.redis.commands.lists.*;

import redis.clients.jedis.Jedis;
import vertx.tests.busmods.redisclient.commands.CommandTest;

public class ListCommandsTest extends CommandTest {

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
	
	@Test
	public void testLSet () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		
		context.getClient().lpush(key, "one");
		context.getClient().lpush(key, "two");
		context.getClient().lpush(key, "three");
		
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("value", value);
		request.putNumber("index", 0);
		
		
		TestMessage msg = getMessage(request); 
		
		LSetCommand cmd = new LSetCommand();
		cmd.handle(msg, context);
		
		String response = msg.reply.getString("value");
		assertEquals("OK", response);
		
		String ret_value = context.getClient().lindex(key, 0);
		
		assertEquals(value, ret_value);
	}
	
	
}
