/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package vertx.tests.core.eventbus;

import org.vertx.java.core.AsyncResult;
import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.eventbus.ReplyException;
import org.vertx.java.core.eventbus.ReplyFailure;
import org.vertx.java.core.eventbus.impl.ReplyFailureMessage;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;
import org.vertx.java.testframework.TestUtils;

import java.util.Random;

/**
 *
 * The echo tests test that different message types are serialized and
 * deserialzed properly across the network
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class LocalEchoClient extends EventBusAppBase {

  private static final Logger log = LoggerFactory.getLogger(LocalEchoClient.class);

  public final static long TIMEOUT = 1500;

  private String echoAddress() {
    String echoAddress = (String)vertx.sharedData().getMap("echoaddress").get("echoaddress");
    return echoAddress;
  }
  
  @Override
  public void start(final Future<Void> startedResult) {
    super.start(startedResult);
  }

  @Override
  public void stop() {
    super.stop();
  }

  protected boolean isLocal() {
    return true;
  }

  public void testEchoString() {
    String msg = TestUtils.randomUnicodeString(1000);
    Handler<Message<String>> hndlr = echoHandler(msg);
    eb.send(echoAddress(), msg, hndlr);
  }

  public void testEchoNullString() {
    String msg = null;
    Handler<Message<String>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoLong() {
    // must use new Long instance, not autoboxing
    Long msg = new Long(new Random().nextLong());
    Handler<Message<Long>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoNullLong() {
    Long msg = null;
    Handler<Message<Long>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoInt() {
    // must use new Integer instance, not autoboxing
    Integer msg = new Integer(new Random().nextInt());
    Handler<Message<Integer>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoNullInt() {
    Integer msg = null;
    Handler<Message<Integer>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoShort() {
    // must use new Short instance, not autoboxing
    Short msg = new Short((short)(new Random().nextInt(Short.MAX_VALUE)));
    Handler<Message<Short>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoNullShort() {
    Short msg = null;
    Handler<Message<Short>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoByte() {
    Byte msg = (byte)(new Random().nextInt(Byte.MAX_VALUE));
    Handler<Message<Byte>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoNullByte() {
    Byte msg = null;
    Handler<Message<Byte>> handler = echoHandler(msg);
    eb.send(echoAddress(), msg, handler);
  }

  public void testEchoBooleanTrue() {
    Boolean tru = Boolean.TRUE;
    Handler<Message<Boolean>> handler = echoHandler(tru);
    eb.send(echoAddress(), tru, handler);
  }

  public void testEchoBooleanFalse() {
    Boolean fal = Boolean.FALSE;
    Handler<Message<Boolean>> handler = echoHandler(fal);
    eb.send(echoAddress(), fal, handler);
  }

  public void testEchoNullBoolean() {
    Boolean fal = null;
    Handler<Message<Boolean>> handler = echoHandler(fal);
    eb.send(echoAddress(), fal, handler);
  }

  public void testEchoByteArray() {
    byte[] bytes = TestUtils.generateRandomByteArray(1000);
    Handler<Message<byte[]>> handler = echoHandler(bytes);
    eb.send(echoAddress(), bytes, handler);
  }

  public void testEchoNullByteArray() {
    byte[] bytes = null;
    Handler<Message<byte[]>> handler = echoHandler(bytes);
    eb.send(echoAddress(), bytes, handler);
  }

  public void testEchoFloat() {
    Float fl = new Random().nextInt() / 37.0f;
    Handler<Message<Float>> handler = echoHandler(fl);
    eb.send(echoAddress(), fl, handler);
  }

  public void testEchoNullFloat() {
    Float fl = null;
    Handler<Message<Float>> handler = echoHandler(fl);
    eb.send(echoAddress(), fl, handler);
  }

  public void testEchoDouble() {
    Double db = new Random().nextInt() / 37.0d;
    Handler<Message<Double>> handler = echoHandler(db);
    eb.send(echoAddress(), db, handler);
  }

  public void testEchoNullDouble() {
    Double db = null;
    Handler<Message<Double>> handler = echoHandler(db);
    eb.send(echoAddress(), db, handler);
  }

  public void testEchoBuffer() {
    Buffer buff = TestUtils.generateRandomBuffer(1000);
    Handler<Message<Buffer>> handler = echoHandler(buff);
    eb.send(echoAddress(), buff, handler);
  }

  public void testEchoNullBuffer() {
    Buffer buff = null;
    Handler<Message<Buffer>> handler = echoHandler(buff);
    eb.send(echoAddress(), buff, handler);
  }

  public void testEchoJson() {
    JsonObject obj = new JsonObject();
    obj.putString("foo", "bar");
    obj.putNumber("num", 12124);
    obj.putBoolean("x", true);
    obj.putBoolean("y", false);
    Handler<Message<JsonObject>> handler = echoHandler(obj);
    eb.send(echoAddress(), obj, handler);
  }

  public void testEchoNullJson() {
    JsonObject obj = null;
    Handler<Message<JsonObject>> handler = echoHandler(obj);
    eb.send(echoAddress(), obj, handler);
  }

  public void testEchoJsonArray() {
    JsonArray obj = new JsonArray();
    obj.add("foo");
    obj.add(12);
    obj.add(true);
    obj.add(false);
    Handler<Message<JsonArray>> handler = echoHandler(obj);
    eb.send(echoAddress(), obj, handler);
  }

  public void testEchoNullJsonArray() {
    JsonArray obj = null;
    Handler<Message<JsonArray>> handler = echoHandler(obj);
    eb.send(echoAddress(), obj, handler);
  }


  public void testEchoCharacter() {
    // must use new Character instance, not autoboxing
    Character chr = new Character((char)(new Random().nextInt()));
    Handler<Message<Character>> handler = echoHandler(chr);
    eb.send(echoAddress(), chr, handler);
  }

  public void testEchoNullCharacter() {
    Character chr = null;
    Handler<Message<Character>> handler = echoHandler(chr);
    eb.send(echoAddress(), chr, handler);
  }

  public void testSendWithTimeoutReply() {
    String address = "some-address";
    eb.sendWithTimeout(address, "foo", 500, new Handler<AsyncResult<Message<String>>>() {
      @Override
      public void handle(AsyncResult<Message<String>> reply) {
        tu.azzert(reply.succeeded());
        tu.azzert("bar".equals(reply.result().body()));
        tu.testComplete();
      }
    });
  }

  public void testSendWithTimeoutNoReply() {
    final long start = System.currentTimeMillis();

    String address = "some-address";
    eb.sendWithTimeout(address, "foo", TIMEOUT, new Handler<AsyncResult<Message<String>>>() {
      boolean replied;
      @Override
      public void handle(AsyncResult<Message<String>> reply) {
        tu.azzert(!replied);
        tu.azzert(reply.failed());
        tu.azzert(System.currentTimeMillis() - start >= TIMEOUT);
        // Now wait a bit longer in case a reply actually comes
        vertx.setTimer(TIMEOUT * 2, new Handler<Long>() {
          @Override
          public void handle(Long tid) {
            tu.testComplete();
          }
        });
        replied = true;
      }
    });
  }

  public void testSendReplyWithTimeoutNoReplyHandler() {
    String address = "some-address";
    eb.send(address, "foo");
  }

  public void testSendWithDefaultTimeoutNoReply() {

    eb.setDefaultReplyTimeout(TIMEOUT);
    String address = "some-address";
    eb.send(address, "foo", new Handler<Message<String>>() {
      @Override
      public void handle(Message<String> reply) {
        tu.azzert(false, "reply handler should not be called");
      }
    });
    // Wait a bit in case a reply comes
    vertx.setTimer(TIMEOUT * 3, new Handler<Long>() {
      @Override
      public void handle(Long tid) {
        tu.testComplete();
      }
    });
    eb.setDefaultReplyTimeout(-1);
  }

  public void testReplyWithTimeoutReply() {

    String address = "some-address";
    eb.send(address, "foo", new Handler<Message<String>>() {
      @Override
      public void handle(Message<String> reply) {
        tu.azzert("bar".equals(reply.body()));
        reply.reply("quux");
      }
    });
  }

  public void testReplyWithTimeoutNoReply() {

    String address = "some-address";
    final long start = System.currentTimeMillis();
    eb.send(address, "foo", new Handler<Message<String>>() {
      @Override
      public void handle(final Message<String> reply) {
        tu.azzert("bar".equals(reply.body()));
        // Delay the reply
        vertx.setTimer(TIMEOUT * 2, new Handler<Long>() {
          @Override
          public void handle(Long tid) {
            reply.reply("quux");
          }
        });
      }
    });
  }

  public void testReplyNoHandlers() {
    String address = "some-address";

    eb.sendWithTimeout(address, "foo", TIMEOUT, new Handler<AsyncResult<Message<String>>>() {
      @Override
      public void handle(AsyncResult<Message<String>> reply) {
        tu.azzert(reply.failed());
        tu.azzert(reply.cause() instanceof ReplyException);
        ReplyException ex = (ReplyException)reply.cause();
        tu.azzert(ex.failureType() == ReplyFailure.NO_HANDLERS);
        tu.testComplete();
      }
    });
  }

  public void testReplyRecipientFailure() {
    String address = "some-address";

    final String msg = "too many giraffes";

    eb.sendWithTimeout(address, "foo", TIMEOUT, new Handler<AsyncResult<Message<String>>>() {
      @Override
      public void handle(AsyncResult<Message<String>> reply) {
        tu.azzert(reply.failed());
        tu.azzert(reply.cause() instanceof ReplyException);
        ReplyException ex = (ReplyException)reply.cause();
        tu.azzert(ex.failureType() == ReplyFailure.RECIPIENT_FAILURE);
        tu.azzert(23 == ex.failureCode());
        tu.azzert(msg.equals(ex.getMessage()));
        tu.testComplete();
      }
    });
  }

  public void testReplyRecipientFailureStandardHandler() {
    String address = "some-address";
    final String msg = "too many giraffes";
    eb.send(address, "foo", new Handler<Message>() {
      @Override
      public void handle(Message message) {
        tu.azzert((message instanceof ReplyFailureMessage));
        ReplyFailureMessage rfmsg = (ReplyFailureMessage)message;
        tu.azzert(ReplyFailure.RECIPIENT_FAILURE == rfmsg.body().failureType());
        tu.azzert(23 == rfmsg.body().failureCode());
        tu.azzert(msg.equals(rfmsg.body().getMessage()));
        tu.testComplete();
      }
    });
  }

  private <T> Handler<Message<T>> echoHandler(final Object msg) {
    Handler<Message<T>> handler = new Handler<Message<T>>() {
      public void handle(Message reply) {
        tu.checkThread();
        if (msg == null) {
          tu.azzert(reply.body() == null);
        } else {
          if (!(msg instanceof byte[])) {
            tu.azzert(msg.equals(reply.body()), "Expecting " + msg + " got " + reply.body());
          } else {
            TestUtils.byteArraysEqual((byte[])msg, (byte[])reply.body());
          }
          // Bytes and Booleans are never copied since cached in the JVM
          if ((!isLocal() && !(msg instanceof Byte) && !(msg instanceof Boolean)) ||
              (isLocal() && ((msg instanceof Buffer) || (msg instanceof byte[]) || (msg instanceof JsonObject) || (msg instanceof JsonArray)))) {
            // Should be copied
            tu.azzert(msg != reply.body());
          } else {
            // Shouldn't be copied
            tu.azzert(msg == reply.body());
          }
        }
        eb.unregisterHandler(echoAddress(), this);
        tu.testComplete();
      }
    };
    return handler;
  }

}
