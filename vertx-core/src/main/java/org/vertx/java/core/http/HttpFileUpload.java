package org.vertx.java.core.http;

import java.nio.charset.Charset;

public class HttpFileUpload extends HttpData {

  private final String filename;
  private final Charset charset;
  private final String contentType;

  public HttpFileUpload(String name, String filename, Charset charset, String contentType) {
    super(name);
    this.filename = filename;
    this.charset = charset;
    this.contentType = contentType;
  }

  public String getFilename() {
    return filename;
  }

  public Charset getCharset() {
    return charset;
  }

  public String getContentType() {
    return contentType;
  }

  @Override
  public boolean isFileUpload() {
    return true;
  }

  @Override
  public String toString() {
    return getClass().getSimpleName() + " [name=" + getName() + ", " +
      "filename=" + filename + ", " +
      "charset=" + charset + ", " +
      "contentType=" + contentType + "]";
  }

}
