package io.vertx.core.eventbus.impl;

import io.vertx.core.eventbus.EventBus;
import io.vertx.core.impl.VertxImpl;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 @author Andrey Fink 2023-09-09
 @see io.vertx.core.eventbus.impl.MessageConsumerImpl */
public class MessageConsumerImplTest extends VertxTestBase  {

  protected EventBus eb;

  @Test public void testBits (){
    var mci = new MessageConsumerImpl<String>(( (VertxImpl) vertx ).getOrCreateContext(), (EventBusImpl) eb, "x.y", true);

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
}
