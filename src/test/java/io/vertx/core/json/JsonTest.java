package io.vertx.core.json;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class JsonTest {

  @Test
  public void testBase64Conversion() {
    // demo string
    assertEquals("qL8R4QIcQ/ZsRqOAbeRfcZhilN/MksRtDaErMA==", Json.toBase64("qL8R4QIcQ_ZsRqOAbeRfcZhilN_MksRtDaErMA"));
    // verify if 1 pad is added
    assertEquals("SGVsbG8gVmVydC54ISE=", Json.toBase64("SGVsbG8gVmVydC54ISE"));
    // verify if 2 pads is added
    assertEquals("SGVsbG8gVmVydC54IQ==", Json.toBase64("SGVsbG8gVmVydC54IQ"));
  }
}
