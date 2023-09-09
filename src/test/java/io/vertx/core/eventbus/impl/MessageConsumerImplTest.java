package io.vertx.core.eventbus.impl;

import io.vertx.core.Handler;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.impl.VertxImpl;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;

/**
 @author Andrey Fink 2023-09-09
 @see io.vertx.core.eventbus.impl.MessageConsumerImpl */
public class MessageConsumerImplTest extends VertxTestBase {

  @Test public void testBits (){
    var mci = new MessageConsumerImpl<String>(( (VertxImpl) vertx ).getOrCreateContext(), null, "x.y", true);

    assertTrue(mci.is(MessageConsumerImpl.OPT_LOCAL_ONLY));
    assertFalse(mci.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(mci.is(MessageConsumerImpl.OPT_HANDLER));

    mci.enable(MessageConsumerImpl.OPT_REGISTERED);
    assertTrue(mci.is(MessageConsumerImpl.OPT_LOCAL_ONLY));
    assertTrue(mci.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(mci.is(MessageConsumerImpl.OPT_HANDLER));

    mci.enable(MessageConsumerImpl.OPT_HANDLER);
    assertTrue(mci.is(MessageConsumerImpl.OPT_LOCAL_ONLY));
    assertTrue(mci.is(MessageConsumerImpl.OPT_REGISTERED));
    assertTrue(mci.is(MessageConsumerImpl.OPT_HANDLER));

    //

    mci.disable(MessageConsumerImpl.OPT_HANDLER);
    assertTrue(mci.is(MessageConsumerImpl.OPT_LOCAL_ONLY));
    assertTrue(mci.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(mci.is(MessageConsumerImpl.OPT_HANDLER));

    mci.disable(MessageConsumerImpl.OPT_REGISTERED);
    assertTrue(mci.is(MessageConsumerImpl.OPT_LOCAL_ONLY));
    assertFalse(mci.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(mci.is(MessageConsumerImpl.OPT_HANDLER));

    mci.disable(MessageConsumerImpl.OPT_LOCAL_ONLY);
    assertFalse(mci.is(MessageConsumerImpl.OPT_LOCAL_ONLY));
    assertFalse(mci.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(mci.is(MessageConsumerImpl.OPT_HANDLER));
  }


  @Test public void testSimple () throws InterruptedException{
    var q = new LinkedBlockingQueue<Message<Integer>>();
    var eb = vertx.eventBus();
    Handler<Message<Integer>> add = q::add;

    var consumer = (MessageConsumerImpl<Integer>) eb.consumer("x.y.z", add);
    assertSame(add, consumer.getHandler());
    assertTrue(consumer.is(MessageConsumerImpl.OPT_REGISTERED));
    assertTrue(consumer.is(MessageConsumerImpl.OPT_HANDLER));

    eb.send("x.y.z", 42);
    var msg = q.take();
    assertEquals(42, msg.body().intValue());

    consumer.handler(null);
    assertSame(add, consumer.getHandler());// not null! :-)
    assertFalse(consumer.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(consumer.is(MessageConsumerImpl.OPT_HANDLER));

    // dev/null
    eb.send("x.y.z", 45);
    assertTrue(q.isEmpty());// but not used ^
  }

  @Test public void testNoHandler () throws InterruptedException{
    var eb = vertx.eventBus();

    var consumer = (MessageConsumerImpl<Integer>) eb.<Integer>consumer("x.y.z");
    assertNull(consumer.getHandler());
    assertFalse(consumer.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(consumer.is(MessageConsumerImpl.OPT_HANDLER));

    eb.send("x.y.z", 42);

    consumer.handler(null);
    assertNull(consumer.getHandler());
    assertFalse(consumer.is(MessageConsumerImpl.OPT_REGISTERED));
    assertFalse(consumer.is(MessageConsumerImpl.OPT_HANDLER));
  }
}
