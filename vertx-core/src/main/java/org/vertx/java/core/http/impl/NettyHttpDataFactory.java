package org.vertx.java.core.http.impl;

import java.io.IOException;
import java.nio.charset.Charset;

import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.multipart.Attribute;
import org.jboss.netty.handler.codec.http.multipart.FileUpload;
import org.jboss.netty.handler.codec.http.multipart.HttpDataFactory;
import org.jboss.netty.handler.codec.http.multipart.InterfaceHttpData;
import org.vertx.java.core.http.HttpAttribute;
import org.vertx.java.core.http.HttpFileUpload;
import org.vertx.java.core.http.HttpMultipartFormElementHandler;

public class NettyHttpDataFactory implements HttpDataFactory {

  private final HttpMultipartFormElementHandler handler;

  public NettyHttpDataFactory(HttpMultipartFormElementHandler handler) {
    this.handler = handler;
  }

  @Override
  public Attribute createAttribute(HttpRequest request, String name) {
    HttpAttribute delegate = new HttpAttribute(name);
    handler.handle(delegate);
    return new NettyHttpAttribute(delegate, name);
  }

  @Override
  public Attribute createAttribute(HttpRequest request, String name, String value) {
    HttpAttribute delegate = new HttpAttribute(name);
    handler.handle(delegate);
    try {
      return new NettyHttpAttribute(delegate, name, value);
    } catch (IOException e) {
        throw new IllegalArgumentException(e);
    }
  }

  @Override
  public FileUpload createFileUpload(HttpRequest request, String name, String filename, String contentType,
      String contentTransferEncoding, Charset charset, long size) {
    HttpFileUpload delegate = new HttpFileUpload(name, filename, charset, contentType);
    handler.handle(delegate);
    return new NettyFileUpload(delegate, name, filename, contentType,
        contentTransferEncoding, charset, size);
  }

  @Override
  public void removeHttpDataFromClean(HttpRequest request, InterfaceHttpData data) {
  }

  @Override
  public void cleanRequestHttpDatas(HttpRequest request) {
  }

  @Override
  public void cleanAllHttpDatas() {
  }

}
