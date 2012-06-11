package vertx.tests.busmods.redisclient.commands.hashes;

import static org.junit.Assert.*;

import java.util.List;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.CommandException;
import org.vertx.mods.redis.commands.hashes.*;
import org.vertx.mods.redis.commands.keys.KeysCommand;

import redis.clients.jedis.Jedis;
import vertx.tests.busmods.redisclient.commands.CommandTest;

public class HashCommandsTest extends CommandTest {

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
	public void testHDel () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String value = getUniqueString();
		
		context.getClient().hset(key, field, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		JsonArray fields = new JsonArray();
		fields.addString(field);
		request.putArray("fields", fields);
		
		TestMessage msg = getMessage(request); 
		
		HDelCommand cmd = new HDelCommand();
		cmd.handle(msg, context);
		
		assertFalse(context.getClient().hexists(key, field));
	}
	@Test
	public void testHExists () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String value = getUniqueString();
		
		context.getClient().hset(key, field, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("field", field);
		
		TestMessage msg = getMessage(request); 
		
		HExistsCommand cmd = new HExistsCommand();
		cmd.handle(msg, context);
		
		assertTrue(msg.reply.getBoolean("value"));
	}
	@Test
	public void testHGet () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String value = getUniqueString();
		
		context.getClient().hset(key, field, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("field", field);
		
		TestMessage msg = getMessage(request); 
		
		HGetCommand cmd = new HGetCommand();
		cmd.handle(msg, context);
		
		assertEquals(value, msg.reply.getString("value"));
	}
	@Test
	public void testHIncrBy () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		
		
		context.getClient().hset(key, field, "1");
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("field", field);
		request.putNumber("increment", 2);
		
		TestMessage msg = getMessage(request); 
		
		HIncrByCommand cmd = new HIncrByCommand();
		cmd.handle(msg, context);
		
		assertEquals(3, msg.reply.getNumber("value").intValue());
	}
	@Test
	public void testHKeys () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String value = getUniqueString();
		
		String field2 = getUniqueString();
		
		context.getClient().hset(key, field, value);
		context.getClient().hset(key, field2, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request); 
		
		HKeysCommand cmd = new HKeysCommand();
		cmd.handle(msg, context);
		
		assertEquals(2, msg.reply.getArray("value").size());
		assertTrue(msg.reply.getArray("value").contains(field));
		assertTrue(msg.reply.getArray("value").contains(field2));
	}
	@Test
	public void testHLen () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String value = getUniqueString();
		
		
		String field2 = getUniqueString();
		
		context.getClient().hset(key, field, value);
		context.getClient().hset(key, field2, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request); 
		
		HLenCommand cmd = new HLenCommand();
		cmd.handle(msg, context);
		
		assertEquals(2, msg.reply.getNumber("value").intValue());
	}
	
	@Test
	public void testHMGet () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String field2 = getUniqueString();
		
		context.getClient().hset(key, field, "1");
		context.getClient().hset(key, field2, "2");
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		JsonArray fields = new JsonArray();
		fields.addString(field);
		fields.addString(field2);
		request.putArray("fields", fields);
		
		TestMessage msg = getMessage(request); 
		
		HMGetCommand cmd = new HMGetCommand();
		cmd.handle(msg, context);
		
		assertEquals(2, msg.reply.getArray("value").size());
		assertTrue(msg.reply.getArray("value").contains("1"));
		assertTrue(msg.reply.getArray("value").contains("2"));
	}
	
	@Test
	public void testHMSet () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String field2 = getUniqueString();
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		JsonObject fields = new JsonObject();
		fields.putString(field, "1");
		fields.putString(field2, "2");
		request.putObject("fields", fields);
		
		TestMessage msg = getMessage(request); 
		
		HMSetCommand cmd = new HMSetCommand();
		cmd.handle(msg, context);
		
		List<String> values = context.getClient().hmget(key, field, field2);
		
		assertEquals(2, values.size());
		assertTrue(values.contains("1"));
		assertTrue(values.contains("2"));
	}
	
	@Test
	public void testHSet () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String value = getUniqueString();
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("field", field);
		request.putString("value", value);
		
		TestMessage msg = getMessage(request); 
		
		HSetCommand cmd = new HSetCommand();
		cmd.handle(msg, context);
		
		String ret_value = context.getClient().hget(key, field);
		
		assertNotNull(ret_value);
		assertEquals(value, ret_value);
	} 
	
	@Test
	public void testHVals () throws CommandException {
		String key = getUniqueString();
		String field = getUniqueString();
		String field2 = getUniqueString();
		
		context.getClient().hset(key, field, "1");
		context.getClient().hset(key, field2, "2");
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request); 
		
		HValsCommand cmd = new HValsCommand();
		cmd.handle(msg, context);
		
		assertEquals(2, msg.reply.getArray("value").size());
		assertTrue(msg.reply.getArray("value").contains("1"));
		assertTrue(msg.reply.getArray("value").contains("2"));
	}
	
}
