package vertx.tests.busmods.redisclient.commands.keys;

import static org.junit.Assert.*;

import java.util.Calendar;
import java.util.Date;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.CommandException;
import org.vertx.mods.redis.commands.keys.DelCommand;
import org.vertx.mods.redis.commands.keys.ExistsCommand;
import org.vertx.mods.redis.commands.keys.ExpireAtCommand;
import org.vertx.mods.redis.commands.keys.ExpireCommand;
import org.vertx.mods.redis.commands.keys.KeysCommand;
import org.vertx.mods.redis.commands.keys.MoveCommand;
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

public class KeyCommandsTest extends CommandTest {

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
	public void testExistCommand() throws CommandException {
		
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		
		context.getClient().set(k1, v1);
		
		JsonObject request = new JsonObject();
		request.putString("key", k1);
		
		ExistsCommand cmd = new ExistsCommand();
		
		TestMessage msg = getMessage(request);
		cmd.handle(msg, context);
		
		boolean reply = msg.reply.getBoolean("value");
		assertTrue(reply);
	}
	
	@Test
	public void testDelCommand() throws CommandException {
		
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		
		context.getClient().set(k1, v1);
		
		assertTrue(context.getClient().exists(k1));
		
		DelCommand cmd = new DelCommand();
		
		JsonObject request = new JsonObject();
		JsonArray keys = new JsonArray();
		keys.addString(k1);
		request.putArray("keys", keys);
		
		TestMessage msg = getMessage(request);
		cmd.handle(msg, context);
		
		
		assertFalse(context.getClient().exists(k1));
	}
	
	@Test
	public void testKeysCommand() throws CommandException {
		
		String k1 = "k"+getUniqueString();
		String v1 = getUniqueString();
		String k2 = "k"+getUniqueString();
		String v2 = getUniqueString();
		String k3 = "o"+getUniqueString();
		String v3 = getUniqueString();
		
		context.getClient().set(k1, v1);
		context.getClient().set(k2, v2);
		context.getClient().set(k3, v3);
		
		assertTrue(context.getClient().exists(k1));
		
		KeysCommand cmd = new KeysCommand();
		
		JsonObject request = new JsonObject();
		request.putString("pattern", "k*");
		
		TestMessage msg = getMessage(request);
		cmd.handle(msg, context);
		
		JsonArray reply = msg.reply.getArray("value");
		assertTrue(reply.contains(k1));
		assertTrue(reply.contains(k2));
		assertFalse(reply.contains(k3));
	}
	
	@Test
	public void testExpireCommand() throws CommandException {
		
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		
		context.getClient().set(k1, v1);
		
		assertTrue(context.getClient().exists(k1));
		
		ExpireCommand cmd = new ExpireCommand();
		
		JsonObject request = new JsonObject();
		request.putString("key", k1);
		request.putNumber("seconds", 3);
		
		TestMessage msg = getMessage(request);
		cmd.handle(msg, context);
		assertEquals(1, msg.reply.getNumber("value").intValue());
		
		try {
			Thread.sleep(5000);
		} catch (Exception e) {
			throw new CommandException(e);
		}
		
		assertFalse(context.getClient().exists(k1));
	}
	
	@Test
	public void testExpireAtCommand() throws CommandException {
		
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		
		context.getClient().set(k1, v1);
		
		assertTrue(context.getClient().exists(k1));
		
		ExpireAtCommand cmd = new ExpireAtCommand();
		
		JsonObject request = new JsonObject();
		request.putString("key", k1);

		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.SECOND, 5);
		
		
		request.putNumber("timestamp", toUnixTimeStamp(cal.getTimeInMillis()));
		
		TestMessage msg = getMessage(request);
		cmd.handle(msg, context);
		assertEquals(1, msg.reply.getNumber("value").intValue());
		
		try {
			Thread.sleep(7000);
		} catch (Exception e) {
			throw new CommandException(e);
		}
		
		assertFalse(context.getClient().exists(k1));
	}
	
	@Test
	public void testMoveCommand() throws CommandException {
		
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		
		context.getClient().set(k1, v1);
		
		assertTrue(context.getClient().exists(k1));
		
		MoveCommand cmd = new MoveCommand();
		
		JsonObject request = new JsonObject();
		request.putString("key", k1);
		request.putNumber("index", 5);
		
		TestMessage msg = getMessage(request);
		cmd.handle(msg, context);
		
		assertFalse(context.getClient().exists(k1));
		
		context.getClient().select(5);
		assertTrue(context.getClient().exists(k1));
		context.getClient().select(0);
	}
}
