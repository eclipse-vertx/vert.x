package io.vertx.test.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.test.core.VertxTestBase;

import org.apache.directory.shared.ldap.aci.UserClass.ThisEntry;
import org.junit.Test;
import org.omg.CORBA.SystemException;

public class MessageTest extends VertxTestBase {
	//setup a few verticles and test the forward

	@Override
	public void setUp() throws Exception {
		super.setUp();
		vertx.deployVerticle(new FirstVerticle());
		vertx.deployVerticle(new SecondVerticle());
	}

	@Test
	public void testForward() throws Exception{
		System.out.println("testForward()");
		vertx.eventBus().send("io.vertx.test.core.FirstVerticle", "first");
		//await();
	}
	
	public class FirstVerticle extends AbstractVerticle implements Handler<Message<String>>{
		
		@Override
		public void start() throws Exception {
			super.start();
			vertx.eventBus().registerHandler("io.vertx.test.core.FirstVerticle", this);
		}

		@Override
		public void handle(Message<String> event) {
					
			System.out.println("Recieved: " + event.body());					
			
			if(!event.isForward()){
				event.forward("io.vertx.test.core.SecondVerticle");				
			}
			else{
				assertTrue(event.isForward());
				System.out.println(event.body());
			}
			
		}
	}

	public class SecondVerticle extends AbstractVerticle implements Handler<Message<String>>{

		@Override
		public void start() throws Exception {
			super.start();
			vertx.eventBus().registerHandler("io.vertx.test.core.SecondVerticle", this);

		}

		@Override
		public void handle(Message<String> event) {			
			assertTrue(event.isForward());
			System.out.println(event.body());
			event.forward("io.vertx.test.core.FirstVerticle");
		}
	}
}
