/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vertx.tests.core.eventbus;

import org.vertx.java.core.Future;
import org.vertx.java.core.Handler;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.Message;
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
