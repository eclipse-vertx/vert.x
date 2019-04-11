package io.vertx.core.json;

import io.vertx.core.json.codecs.*;
import org.junit.Before;
import org.junit.Test;

import static io.vertx.core.json.Json.encodeToBuffer;
import static org.junit.Assert.assertEquals;

public class JsonCodecLoaderTest {

  JsonCodecLoader jsonCodecLoader;

  @Before
  public void setUp() throws Exception {
    this.jsonCodecLoader = JsonCodecLoader.loadCodecsFromSPI();
  }

  @Test
  public void booleanCodecTest() {
    MyBooleanPojo pojo = new MyBooleanPojo();
    pojo.setValue(true);
    assertEquals(encodeToBuffer(true), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer(true), MyBooleanPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void booleanCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer("aaa"), MyBooleanPojo.class);
  }

  @Test
  public void charCodecTest() {
    MyCharPojo pojo = new MyCharPojo();
    pojo.setValue('a');
    assertEquals(encodeToBuffer('a'), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer('a'), MyCharPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void charCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(1), MyCharPojo.class);
  }

  @Test
  public void doubleCodecTest() {
    MyDoublePojo pojo = new MyDoublePojo();
    pojo.setValue(1.2d);
    assertEquals(encodeToBuffer(1.2d), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer(1.2d), MyDoublePojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void doubleCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(1L), MyDoublePojo.class);
  }

  @Test
  public void floatCodecTest() {
    MyFloatPojo pojo = new MyFloatPojo();
    pojo.setValue(1.2f);
    assertEquals(encodeToBuffer(1.2f), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer(1.2f), MyFloatPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void floatCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(1L), MyFloatPojo.class);
  }

  @Test
  public void intCodecTest() {
    MyIntegerPojo pojo = new MyIntegerPojo();
    pojo.setValue(1);
    assertEquals(encodeToBuffer((int)1), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer((int)1), MyIntegerPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void intCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(1.2), MyIntegerPojo.class);
  }

  @Test
  public void longCodecTest() {
    MyLongPojo pojo = new MyLongPojo();
    pojo.setValue(1L);
    assertEquals(encodeToBuffer(1L), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer(1L), MyLongPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void longCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(1.2), MyLongPojo.class);
  }

  @Test
  public void shortCodecTest() {
    MyShortPojo pojo = new MyShortPojo();
    pojo.setValue((short)1);
    assertEquals(encodeToBuffer((short)1), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(encodeToBuffer((short)1), MyShortPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void shortCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(1.2), MyShortPojo.class);
  }

  @Test
  public void jsonArrayCodecTest() {
    MyJsonArrayPojo pojo = new MyJsonArrayPojo();
    JsonArray array = new JsonArray().add(1).add(2).add(3);
    pojo.setValue(array);
    assertEquals(array.toBuffer(), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(array.toBuffer(), MyJsonArrayPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void jsonArrayCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(2), MyJsonArrayPojo.class);
  }

  @Test
  public void jsonObjectCodecTest() {
    MyJsonObjectPojo pojo = new MyJsonObjectPojo();
    JsonObject obj = new JsonObject().put("a", 1).put("b", "c");
    pojo.setValue(obj);
    assertEquals(obj.toBuffer(), jsonCodecLoader.encodeBuffer(pojo));
    assertEquals(pojo, jsonCodecLoader.decodeBuffer(obj.toBuffer(), MyJsonObjectPojo.class));
  }

  @Test(expected = ClassCastException.class)
  public void jsonObjectCodecWrongTypeTest() {
    jsonCodecLoader.decodeBuffer(encodeToBuffer(2), MyJsonObjectPojo.class);
  }
  
}
