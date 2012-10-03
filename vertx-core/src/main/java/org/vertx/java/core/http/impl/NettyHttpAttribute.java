package org.vertx.java.core.http.impl;

import java.io.IOException;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.handler.codec.http.multipart.MemoryAttribute;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpAttribute;

public class NettyHttpAttribute extends MemoryAttribute {

  private final HttpAttribute delegate;

  public NettyHttpAttribute(HttpAttribute delegate, String name, String value) throws IOException {
    super(name, value);
    this.delegate = delegate;
    setValue(value);
  }

  public NettyHttpAttribute(HttpAttribute delegate, String name) {
    super(name);
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
