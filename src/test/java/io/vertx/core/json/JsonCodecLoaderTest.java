package io.vertx.core.json;

import io.vertx.core.json.codecs.*;
import org.junit.Test;

import static io.vertx.core.json.Json.encodeToBuffer;
import static org.junit.Assert.assertEquals;

public class JsonCodecLoaderTest {

  @Test
  public void booleanCodecTest() {
    MyBooleanPojo pojo = new MyBooleanPojo();
    pojo.setValue(true);
    assertEquals(encodeToBuffer(true), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(encodeToBuffer(true), MyBooleanPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void booleanCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer("aaa"), MyBooleanPojo.class);
  }

  @Test
  public void doubleCodecTest() {
    MyDoublePojo pojo = new MyDoublePojo();
    pojo.setValue(1.2d);
    assertEquals(encodeToBuffer(1.2d), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(encodeToBuffer(1.2d), MyDoublePojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void doubleCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(""), MyDoublePojo.class);
  }

  @Test
  public void floatCodecTest() {
    MyFloatPojo pojo = new MyFloatPojo();
    pojo.setValue(1.2f);
    assertEquals(encodeToBuffer(1.2f), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(encodeToBuffer(1.2f), MyFloatPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void floatCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(""), MyFloatPojo.class);
  }

  @Test
  public void intCodecTest() {
    MyIntegerPojo pojo = new MyIntegerPojo();
    pojo.setValue(1);
    assertEquals(encodeToBuffer((int)1), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(encodeToBuffer((int)1), MyIntegerPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void intCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(""), MyIntegerPojo.class);
  }

  @Test
  public void longCodecTest() {
    MyLongPojo pojo = new MyLongPojo();
    pojo.setValue(1L);
    assertEquals(encodeToBuffer(1L), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(encodeToBuffer(1L), MyLongPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void longCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(""), MyLongPojo.class);
  }

  @Test
  public void shortCodecTest() {
    MyShortPojo pojo = new MyShortPojo();
    pojo.setValue((short)1);
    assertEquals(encodeToBuffer((short)1), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(encodeToBuffer((short)1), MyShortPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void shortCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(""), MyShortPojo.class);
  }

  @Test
  public void jsonArrayCodecTest() {
    MyJsonArrayPojo pojo = new MyJsonArrayPojo();
    JsonArray array = new JsonArray().add(1).add(2).add(3);
    pojo.setValue(array);
    assertEquals(array.toBuffer(), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(array.toBuffer(), MyJsonArrayPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void jsonArrayCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(2), MyJsonArrayPojo.class);
  }

  @Test
  public void jsonObjectCodecTest() {
    MyJsonObjectPojo pojo = new MyJsonObjectPojo();
    JsonObject obj = new JsonObject().put("a", 1).put("b", "c");
    pojo.setValue(obj);
    assertEquals(obj.toBuffer(), JsonCodecMapper.encodeBuffer(pojo));
    assertEquals(pojo, JsonCodecMapper.decodeBuffer(obj.toBuffer(), MyJsonObjectPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void jsonObjectCodecWrongTypeTest() {
    JsonCodecMapper.decodeBuffer(encodeToBuffer(2), MyJsonObjectPojo.class);
  }
  
}
