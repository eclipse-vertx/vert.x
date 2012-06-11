package vertx.tests.busmods.redisclient.commands.sets;

import static org.junit.Assert.*;

import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.mods.redis.CommandContext;
import org.vertx.mods.redis.commands.CommandException;
import org.vertx.mods.redis.commands.lists.LSetCommand;
import org.vertx.mods.redis.commands.sets.*;


import redis.clients.jedis.Jedis;
import vertx.tests.busmods.redisclient.commands.CommandTest;
import vertx.tests.busmods.redisclient.commands.CommandTest.TestMessage;

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
	
	@Test
	public void testSAdd () throws CommandException {
		String key = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		JsonArray values = new JsonArray();
		values.addString(v1);
		values.addString(v2);
		request.putArray("members", values);
		
		
		
		TestMessage msg = getMessage(request); 
		
		SAddCommand cmd = new SAddCommand();
		cmd.handle(msg, context);
		
		Number response = msg.reply.getNumber("value");
		assertEquals(2, response.intValue());
		
		Set<String> ret_value = context.getClient().smembers(key);
		
		assertEquals(2, ret_value.size());
		assertTrue(ret_value.contains(v1));
		assertTrue(ret_value.contains(v2));
	}
	
	@Test
	public void testSCard () throws CommandException {
		String key = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		
		context.getClient().sadd(key, v1, v2);
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		
		
		
		TestMessage msg = getMessage(request); 
		
		SCardCommand cmd = new SCardCommand();
		cmd.handle(msg, context);
		
		assertEquals(2, msg.reply.getNumber("value").intValue());
	}
	
	@Test
	public void testSDiff () throws CommandException {
		String k1 = getUniqueString();
		String k2 = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		String v3 = getUniqueString();
		
		context.getClient().sadd(k1, v1, v2);
		context.getClient().sadd(k2, v3, v2);
		
		JsonObject request = new JsonObject();
		
		JsonArray keys = new JsonArray();
		keys.addString(k1);
		keys.addString(k2);
		request.putArray("keys", keys);
		
		
		TestMessage msg = getMessage(request); 
		
		SDiffCommand cmd = new SDiffCommand();
		cmd.handle(msg, context);
		
		JsonArray diff = msg.reply.getArray("value");
		
		assertEquals(1, diff.size());
		assertTrue(diff.contains(v1));
	}
	
	@Test
	public void testSInter () throws CommandException {
		String k1 = getUniqueString();
		String k2 = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		String v3 = getUniqueString();
		
		context.getClient().sadd(k1, v1, v2);
		context.getClient().sadd(k2, v3, v2);
		
		JsonObject request = new JsonObject();
		
		JsonArray keys = new JsonArray();
		keys.addString(k1);
		keys.addString(k2);
		request.putArray("keys", keys);
		
		
		TestMessage msg = getMessage(request); 
		
		SInterCommand cmd = new SInterCommand();
		cmd.handle(msg, context);
		
		JsonArray diff = msg.reply.getArray("value");
		
		assertEquals(1, diff.size());
		assertTrue(diff.contains(v2));
	}
	
	@Test
	public void testSIsMember () throws CommandException {
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		
		context.getClient().sadd(k1, v1);
		
		JsonObject request = new JsonObject();
		request.putString("key", k1);
		request.putString("member", v1);
		
		
		TestMessage msg = getMessage(request); 
		
		SIsMemberCommand cmd = new SIsMemberCommand();
		cmd.handle(msg, context);
				
		assertTrue(msg.reply.getBoolean("value"));
		
		
		// check none member
		request = new JsonObject();
		request.putString("key", k1);
		request.putString("member", v2);
		
		
		msg = getMessage(request); 
		
		cmd.handle(msg, context);
				
		assertFalse(msg.reply.getBoolean("value"));
	}
	
	@Test
	public void testSMembers () throws CommandException {
		String k1 = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		
		context.getClient().sadd(k1, v1, v2);
		
		JsonObject request = new JsonObject();
		request.putString("key", k1);
		
		
		TestMessage msg = getMessage(request); 
		
		SMembersCommand cmd = new SMembersCommand();
		cmd.handle(msg, context);
		
		JsonArray members = msg.reply.getArray("value");
		
		assertEquals(2, members.size());
		assertTrue(members.contains(v1));
		assertTrue(members.contains(v2));
	}
	
	@Test
	public void testSMove () throws CommandException {
		String k1 = getUniqueString();
		String k2 = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		String v3 = getUniqueString();
		
		context.getClient().sadd(k1, v1, v2);
		context.getClient().sadd(k2, v3);
		
		JsonObject request = new JsonObject();
		request.putString("source", k1);
		request.putString("destination", k2);
		request.putString("member", v2);
		
		
		TestMessage msg = getMessage(request); 
		
		SMoveCommand cmd = new SMoveCommand();
		cmd.handle(msg, context);
		
		Set<String> members = context.getClient().smembers(k1);
		
		assertEquals(1, members.size());
		assertTrue(members.contains(v1));
		assertFalse(members.contains(v2));
		
		members = context.getClient().smembers(k2);
		
		assertEquals(2, members.size());
		assertTrue(members.contains(v2));
	}
}
