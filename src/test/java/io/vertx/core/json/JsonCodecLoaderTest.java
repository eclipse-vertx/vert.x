package io.vertx.core.json;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.codecs.MyBooleanPojo;
import io.vertx.core.json.codecs.MyCharPojo;
import org.junit.Before;
import org.junit.Test;

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
    assertEquals(Buffer.buffer("true"), jsonCodecLoader.encode(pojo));
    assertEquals(pojo, jsonCodecLoader.decode(Buffer.buffer("true"), MyBooleanPojo.class));
  }

  @Test
  public void charCodecTest() {
    MyCharPojo pojo = new MyCharPojo();
    pojo.setValue('a');
    assertEquals('a', jsonCodecLoader.decode(Buffer.buffer(new byte[] {(byte)'a'}), MyCharPojo.class));
  }

}
