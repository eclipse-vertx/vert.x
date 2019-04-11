package io.vertx.core.json;

import io.vertx.core.json.codecs.*;
import org.junit.Before;
import org.junit.Test;

import static io.vertx.core.json.Json.encodeToBuffer;
import static org.junit.Assert.assertEquals;

public class JsonCodecLoaderTest {

  @Test
  public void booleanCodecTest() {
    MyBooleanPojo pojo = new MyBooleanPojo();
    pojo.setValue(true);
    assertEquals(encodeToBuffer(true), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(true), MyBooleanPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void booleanCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer("aaa"), MyBooleanPojo.class);
  }

  @Test
  public void doubleCodecTest() {
    MyDoublePojo pojo = new MyDoublePojo();
    pojo.setValue(1.2d);
    assertEquals(encodeToBuffer(1.2d), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(1.2d), MyDoublePojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void doubleCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(""), MyDoublePojo.class);
  }

  @Test
  public void floatCodecTest() {
    MyFloatPojo pojo = new MyFloatPojo();
    pojo.setValue(1.2f);
    assertEquals(encodeToBuffer(1.2f), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(1.2f), MyFloatPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void floatCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(""), MyFloatPojo.class);
  }

  @Test
  public void intCodecTest() {
    MyIntegerPojo pojo = new MyIntegerPojo();
    pojo.setValue(1);
    assertEquals(encodeToBuffer((int)1), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer((int)1), MyIntegerPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void intCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(""), MyIntegerPojo.class);
  }

  @Test
  public void longCodecTest() {
    MyLongPojo pojo = new MyLongPojo();
    pojo.setValue(1L);
    assertEquals(encodeToBuffer(1L), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(1L), MyLongPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void longCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(""), MyLongPojo.class);
  }

  @Test
  public void shortCodecTest() {
    MyShortPojo pojo = new MyShortPojo();
    pojo.setValue((short)1);
    assertEquals(encodeToBuffer((short)1), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer((short)1), MyShortPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void shortCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(""), MyShortPojo.class);
  }

  @Test
  public void jsonArrayCodecTest() {
    MyJsonArrayPojo pojo = new MyJsonArrayPojo();
    JsonArray array = new JsonArray().add(1).add(2).add(3);
    pojo.setValue(array);
    assertEquals(array.toBuffer(), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(array.toBuffer(), MyJsonArrayPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void jsonArrayCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(2), MyJsonArrayPojo.class);
  }

  @Test
  public void jsonObjectCodecTest() {
    MyJsonObjectPojo pojo = new MyJsonObjectPojo();
    JsonObject obj = new JsonObject().put("a", 1).put("b", "c");
    pojo.setValue(obj);
    assertEquals(obj.toBuffer(), JsonCodecLoader.INSTANCE.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecLoader.INSTANCE.decodeBuffer(obj.toBuffer(), MyJsonObjectPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void jsonObjectCodecWrongTypeTest() {
    JsonCodecLoader.INSTANCE.decodeBuffer(encodeToBuffer(2), MyJsonObjectPojo.class);
  }
  
}
