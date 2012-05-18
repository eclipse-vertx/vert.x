package vertx.tests.busmods.redisclient.commands.strings;

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
import org.vertx.mods.redis.commands.strings.AppendCommand;
import org.vertx.mods.redis.commands.strings.DecrByCommand;
import org.vertx.mods.redis.commands.strings.DecrCommand;
import org.vertx.mods.redis.commands.strings.GetCommand;
import org.vertx.mods.redis.commands.strings.GetRangeCommand;
import org.vertx.mods.redis.commands.strings.GetSetCommand;
import org.vertx.mods.redis.commands.strings.IncrByCommand;
import org.vertx.mods.redis.commands.strings.IncrCommand;
import org.vertx.mods.redis.commands.strings.MGetCommand;
import org.vertx.mods.redis.commands.strings.MSetCommand;
import org.vertx.mods.redis.commands.strings.SetCommand;
import org.vertx.mods.redis.commands.strings.SetRangeCommand;
import org.vertx.mods.redis.commands.strings.StrLenCommand;

import redis.clients.jedis.Jedis;
import vertx.tests.busmods.redisclient.commands.CommandTest;

public class StringCommandsTest extends CommandTest {

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
	public void testMSetCommand() throws CommandException {
		JsonObject json = new JsonObject();
		
		JsonObject keyvalues = new JsonObject();
		keyvalues.putString("firstname", "thorsten");
		keyvalues.putString("lastname", "marx");
		
		json.putObject("keyvalues", keyvalues);
		
		MSetCommand cmd = new MSetCommand();
		
		Message msg = getMessage(json);
		cmd.handle(msg, context);
		
		String firstname = context.getClient().get("firstname");
		assertNotNull(firstname);
		assertEquals("thorsten", firstname);
		
		String lastname = context.getClient().get("lastname");
		assertNotNull(lastname);
		assertEquals("marx", lastname);
	}
	
	@Test
	public void testMGetCommand() throws CommandException {
		
		String k1 = getUniqueString();
		String k2 = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		
		context.getClient().set(k1, v1);
		context.getClient().set(k2, v2);
		
		JsonObject json = new JsonObject();
		
		JsonArray keyvalues = new JsonArray();
		keyvalues.add(k1);
		keyvalues.add(k2);
		
		json.putArray("keys", keyvalues);
		
		MGetCommand cmd = new MGetCommand();
		
		TestMessage msg = getMessage(json);
		cmd.handle(msg, context);
		
		JsonArray reply = msg.reply.getArray("value");
		assertEquals(2, reply.size());
		
		assertTrue(reply.contains(v1));
		assertTrue(reply.contains(v2));
	}
	
	@Test
	public void testGetCommand () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request);
		
		GetCommand cmd = new GetCommand();
		cmd.handle(msg, context);
		
		String reply = msg.reply.getString("value");
		assertNotNull(reply);
		assertEquals(value, reply);
	}
	
	@Test
	public void testSetCommand () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("value", value);
		
		TestMessage msg = getMessage(request);
		
		SetCommand cmd = new SetCommand();
		cmd.handle(msg, context);
		
		String reply = msg.reply.getString("value");
		assertEquals("OK", reply);
		
		reply = context.getClient().get(key);
		assertEquals(value, reply);
	}
	
	@Test
	public void testAppendCommand () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		String append = getUniqueString();
		
		String appended = value + append; 
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("value", append);
		
		TestMessage msg = getMessage(request);
		
		AppendCommand cmd = new AppendCommand();
		cmd.handle(msg, context);
		
		Number reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(appended.length(), reply.intValue());
		
		String newValue = context.getClient().get(key);
		assertEquals(value + append, newValue);
	}
	
	@Test
	public void testDecrByCommand () throws CommandException {
		String key = getUniqueString();
		String value = "10";
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putNumber("decrement", new Integer(1));
		
		TestMessage msg = getMessage(request);
		
		DecrByCommand cmd = new DecrByCommand();
		cmd.handle(msg, context);
		
		Number reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(9, reply.intValue());
		
		
		// now test long
		request = new JsonObject();
		request.putString("key", key);
		request.putNumber("decrement", new Long(1));
		
		msg = getMessage(request);
		
		cmd = new DecrByCommand();
		cmd.handle(msg, context);
		
		reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(8, reply.longValue());
	}
	
	@Test
	public void testDecrCommand () throws CommandException {
		String key = getUniqueString();
		String value = "10";
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		
		TestMessage msg = getMessage(request);
		
		DecrCommand cmd = new DecrCommand();
		cmd.handle(msg, context);
		
		Number reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(9, reply.intValue());
		
		
		// now test long
		request = new JsonObject();
		request.putString("key", key);
		
		
		msg = getMessage(request);
		
		cmd = new DecrCommand();
		cmd.handle(msg, context);
		
		reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(8, reply.longValue());
	}
	
	@Test
	public void testIncrByCommand () throws CommandException {
		String key = getUniqueString();
		String value = "10";
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putNumber("increment", new Integer(1));
		
		TestMessage msg = getMessage(request);
		
		IncrByCommand cmd = new IncrByCommand();
		cmd.handle(msg, context);
		
		Number reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(11, reply.intValue());
		
		
		// now test long
		request = new JsonObject();
		request.putString("key", key);
		request.putNumber("increment", new Long(1));
		
		msg = getMessage(request);
		
		cmd = new IncrByCommand();
		cmd.handle(msg, context);
		
		reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(12, reply.longValue());
	}
	
	@Test
	public void testIncrCommand () throws CommandException {
		String key = getUniqueString();
		String value = "10";
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		
		TestMessage msg = getMessage(request);
		
		IncrCommand cmd = new IncrCommand();
		cmd.handle(msg, context);
		
		Number reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(11, reply.intValue());
		
		
		// now test long
		request = new JsonObject();
		request.putString("key", key);
		
		
		msg = getMessage(request);
		
		cmd = new IncrCommand();
		cmd.handle(msg, context);
		
		reply = msg.reply.getNumber("value");
		assertNotNull(reply);
		assertEquals(12, reply.longValue());
	}
	
	@Test
	public void testGetRangeCommand () throws CommandException {
		String key = getUniqueString();
		String value = "This is a sring";
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putNumber("start", 0);
		request.putNumber("end", 3);
		
		TestMessage msg = getMessage(request);
		
		GetRangeCommand cmd = new GetRangeCommand();
		cmd.handle(msg, context);
		
		String reply = msg.reply.getString("value");
		assertNotNull(reply);
		assertEquals("This", reply);
	}
	
	@Test
	public void testGetSetCommand () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		String newValue = getUniqueString();
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("value", newValue);
		
		TestMessage msg = getMessage(request);
		
		GetSetCommand cmd = new GetSetCommand();
		cmd.handle(msg, context);
		
		String reply = msg.reply.getString("value");
		assertNotNull(reply);
		assertEquals(value, reply);
	}
	
	@Test
	public void testStrLenCommand () throws CommandException {
		String key = getUniqueString();
		String value = getUniqueString();
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		TestMessage msg = getMessage(request);
		
		StrLenCommand cmd = new StrLenCommand();
		cmd.handle(msg, context);
		
		Number reply = msg.reply.getNumber("value");
		assertEquals(value.length(), reply.intValue());
	}
	
	@Test
	public void testSetRangeCommand () throws CommandException {
		String key = getUniqueString();
		String value = "Hello World";
		
		context.getClient().set(key, value);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		request.putString("value", "vertx");
		request.putNumber("offset", 6);
		
		TestMessage msg = getMessage(request);
		
		SetRangeCommand cmd = new SetRangeCommand();
		cmd.handle(msg, context);
		
		String reply = context.getClient().get(key);
		assertEquals("Hello vertx", reply);
	}
}
