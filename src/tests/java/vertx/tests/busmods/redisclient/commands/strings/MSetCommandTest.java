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
import org.vertx.mods.redis.commands.strings.MSetCommand;

import redis.clients.jedis.Jedis;

public class MSetCommandTest {

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
	public void testHandle() throws CommandException {
		JsonObject json = new JsonObject();
		JsonArray keyvalues = new JsonArray();
		keyvalues.addString("firstname");
		keyvalues.addString("thorsten");
		keyvalues.addString("lastname");
		keyvalues.addString("marx");
		
		json.putArray("keyvalues", keyvalues);
		
		MSetCommand cmd = new MSetCommand();
		
		Message msg = new TestMessage();
		msg.body = json;
		cmd.handle(msg, context);
		
		String firstname = context.getClient().get("firstname");
		assertNotNull(firstname);
		assertEquals("thorsten", firstname);
		
		String lastname = context.getClient().get("lastname");
		assertNotNull(lastname);
		assertEquals("marx", lastname);
		
	}

	static class TestMessage extends Message<JsonObject> {

		@Override
		public void reply(JsonObject message,
				Handler<Message<JsonObject>> replyHandler) {
			System.out.println(message.toString());
		}
		
	}
}
