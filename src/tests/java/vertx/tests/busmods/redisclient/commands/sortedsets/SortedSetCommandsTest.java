package vertx.tests.busmods.redisclient.commands.sortedsets;

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
import org.vertx.mods.redis.commands.sets.SAddCommand;
import org.vertx.mods.redis.commands.sets.SCardCommand;
import org.vertx.mods.redis.commands.sets.SDiffCommand;
import org.vertx.mods.redis.commands.sets.SInterCommand;
import org.vertx.mods.redis.commands.sets.SIsMemberCommand;
import org.vertx.mods.redis.commands.sets.SMembersCommand;
import org.vertx.mods.redis.commands.sets.SMoveCommand;
import org.vertx.mods.redis.commands.sortedsets.*;

import redis.clients.jedis.Jedis;
import vertx.tests.busmods.redisclient.commands.CommandTest;
import vertx.tests.busmods.redisclient.commands.CommandTest.TestMessage;

public class SortedSetCommandsTest extends CommandTest {

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
	public void testZAdd () throws CommandException {
		String key = getUniqueString();
		String v1 = getUniqueString();
		String v2 = getUniqueString();
		
		
		JsonObject request = new JsonObject();
		request.putString("key", key);
		JsonObject values = new JsonObject();
		values.putNumber(v1, 1d);
		values.putNumber(v2, 2d);
		request.putObject("members", values);
		
		
		
		TestMessage msg = getMessage(request); 
		
		ZAddCommand cmd = new ZAddCommand();
		cmd.handle(msg, context);
		
		Number response = msg.reply.getNumber("value");
		assertEquals(2, response.intValue());
		
		long ret_value = context.getClient().zcount(key, 1, 2);
		
		assertEquals(2, ret_value);
	}
	
	
}
