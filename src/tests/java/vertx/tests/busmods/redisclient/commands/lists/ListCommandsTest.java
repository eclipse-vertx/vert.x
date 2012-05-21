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
	
	@Test 
	public void testLInsert () throws CommandException {
		String key = getUniqueString();
		String value1 = getUniqueString();
		String value2 = getUniqueString();
		String value3 = getUniqueString();
		
		context.getClient().lpush(key, value1);
		context.getClient().lpush(key, value2);
		
		long length = context.getClient().llen(key);
		assertEquals(2, length);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("value", value3);
		request.putString("pivot", value2);
		request.putBoolean("before", true);
		
		TestMessage msg = getMessage(request); 
		
		LInsertCommand cmd = new LInsertCommand();
		cmd.handle(msg, context);
		
		length = context.getClient().llen(key);
		assertEquals(3, length);
	}
	
	@Test 
	public void testLLen () throws CommandException {
		String key = getUniqueString();
		String value1 = getUniqueString();
		String value2 = getUniqueString();
		
		context.getClient().lpush(key, value1);
		context.getClient().lpush(key, value2);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request); 
		
		LLenCommand cmd = new LLenCommand();
		cmd.handle(msg, context);
		
		assertEquals(2, msg.reply.getNumber("value").intValue());
	}
	
	@Test 
	public void testLPop () throws CommandException {
		String key = getUniqueString();
		String value1 = getUniqueString();
		String value2 = getUniqueString();
		
		context.getClient().rpush(key, value1);
		context.getClient().rpush(key, value2);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request); 
		
		LPopCommand cmd = new LPopCommand();
		cmd.handle(msg, context);
		
		assertEquals(value1, msg.reply.getString("value"));
	}
	
	@Test 
	public void testRPop () throws CommandException {
		String key = getUniqueString();
		String value1 = getUniqueString();
		String value2 = getUniqueString();
		
		context.getClient().rpush(key, value1);
		context.getClient().rpush(key, value2);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request); 
		
		RPopCommand cmd = new RPopCommand();
		cmd.handle(msg, context);
		
		assertEquals(value2, msg.reply.getString("value"));
	}
	
	@Test 
	public void testLPush () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		JsonArray values = new JsonArray();
		values.addString(value);
		request.putArray("values", values);
		
		TestMessage msg = getMessage(request); 
		
		LPushCommand cmd = new LPushCommand();
		cmd.handle(msg, context);
		
		// the push command returns the length of the list after the operation
		assertEquals(1, msg.reply.getNumber("value").intValue());
	}
	
}
