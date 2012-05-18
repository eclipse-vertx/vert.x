package vertx.tests.busmods.redisclient.commands;

import java.util.UUID;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonObject;

public abstract class CommandTest {
	
	protected String getUniqueString () {
		return UUID.randomUUID().toString();
	}
	
	protected TestMessage getMessage (JsonObject body) {
		TestMessage msg = new TestMessage();
		msg.body = body;
		
		return msg;
	}
	
	public static class TestMessage extends Message<JsonObject> {

		public JsonObject reply;
		@Override
		public void reply(JsonObject message,
				Handler<Message<JsonObject>> replyHandler) {
			this.reply = message;
//			System.out.println(message.toString());
		}
		
	}
}
