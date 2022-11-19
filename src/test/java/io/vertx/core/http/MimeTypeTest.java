package io.vertx.core.http;

import io.vertx.core.http.impl.MimeMapping;
import org.junit.Assert;
import org.junit.Test;

public class MimeTypeTest {
  @Test
  public void testGetMimeTypeFromPath() {
    String filepath = "/Users/leo/Pictures/IMG_4157.jpg";
    String mimeType = MimeMapping.getMimeTypeForFilename(filepath);
    Assert.assertEquals("get wrong mime type", "image/jpeg", mimeType);
  }

  @Test
  public void testGetMimeTypeCaseInsensitive() {
    String filepath = "/Users/leo/Pictures/IMG_4157.JPG";
    String mimeType = MimeMapping.getMimeTypeForFilename(filepath);
    Assert.assertEquals("get wrong mime type", "image/jpeg", mimeType);
  }
}
