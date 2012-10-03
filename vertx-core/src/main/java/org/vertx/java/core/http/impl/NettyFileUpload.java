package org.vertx.java.core.http.impl;

import java.io.IOException;
import java.nio.charset.Charset;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.multipart.MemoryFileUpload;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpFileUpload;

public class NettyFileUpload extends MemoryFileUpload {

  private final HttpFileUpload delegate;

  public NettyFileUpload(HttpFileUpload delegate, String name, String filename, String contentType, String contentTransferEncoding,
                             Charset charset, long size) {
    super(name, filename, contentType, contentTransferEncoding, charset, size);
    this.delegate = delegate;
  }

  @Override
  public void addContent(ChannelBuffer buffer, boolean isLast) throws IOException {
    if (delegate != null) delegate.handleData(new Buffer(buffer), isLast);
  }

  @Override
  public void setContent(ChannelBuffer buffer) throws IOException {
    if (delegate != null) delegate.handleData(new Buffer(buffer), true);
  }

}
