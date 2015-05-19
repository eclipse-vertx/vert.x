/*
 * Copyright (c) 2011-2013 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 *     The Eclipse Public License is available at
 *     http://www.eclipse.org/legal/epl-v10.html
 *
 *     The Apache License v2.0 is available at
 *     http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.LastHttpContent;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.DefaultHttpDataFactory;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpPostRequestDecoder;
import io.netty.handler.codec.http.multipart.InterfaceHttpData;
import io.netty.util.CharsetUtil;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.CaseInsensitiveHeaders;
import io.vertx.core.http.HttpServerFileUpload;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.http.HttpVersion;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.impl.LoggerFactory;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.SocketAddress;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.security.cert.X509Certificate;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

/**
 * This class is optimised for performance when used on the same event loop that is was passed to the handler with.
 * However it can be used safely from other threads.
 *
 * The internal state is protected using the synchronized keyword. If always used on the same event loop, then
 * we benefit from biased locking which makes the overhead of synchronized near zero.
 *
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class HttpServerRequestImpl implements HttpServerRequest {

  private static final Logger log = LoggerFactory.getLogger(HttpServerRequestImpl.class);

  private final ServerConnection conn;
  private final HttpRequest request;
  private final HttpServerResponse response;

  private io.vertx.core.http.HttpVersion version;
  private io.vertx.core.http.HttpMethod method;
  private String uri;
  private String path;
  private String query;

  private Handler<Buffer> dataHandler;
  private Handler<Throwable> exceptionHandler;

  //Cache this for performance
  private MultiMap params;
  private MultiMap headers;
  private String absoluteURI;

  private NetSocket netSocket;
  private Handler<HttpServerFileUpload> uploadHandler;
  private Handler<Void> endHandler;
  private MultiMap attributes;
  private HttpPostRequestDecoder decoder;
  private boolean isURLEncoded;
  private boolean ended;


  HttpServerRequestImpl(ServerConnection conn,
                        HttpRequest request,
                        HttpServerResponse response) {
    this.conn = conn;
    this.request = request;
    this.response = response;
  }

  @Override
  public synchronized io.vertx.core.http.HttpVersion version() {
    if (version == null) {
      io.netty.handler.codec.http.HttpVersion nettyVersion = request.getProtocolVersion();
      if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_0) {
        version = HttpVersion.HTTP_1_0;
      } else if (nettyVersion == io.netty.handler.codec.http.HttpVersion.HTTP_1_1) {
        version = HttpVersion.HTTP_1_1;
      } else {
        throw new IllegalStateException("Unsupported HTTP version: " + nettyVersion);
      }
    }
    return version;
  }

  @Override
  public synchronized io.vertx.core.http.HttpMethod method() {
    if (method == null) {
      method = io.vertx.core.http.HttpMethod.valueOf(request.getMethod().toString());
    }
    return method;
  }

  @Override
  public synchronized String uri() {
    if (uri == null) {
      uri = request.getUri();
    }
    return uri;
  }

  @Override
  public synchronized String path() {
    if (path == null) {
      path = UriParser.path(uri());
    }
    return path;
  }

  @Override
  public synchronized String query() {
    if (query == null) {
      query = UriParser.query(uri());
    }
    return query;
  }

  @Override
  public HttpServerResponse response() {
    return response;
  }

  @Override
  public synchronized MultiMap headers() {
    if (headers == null) {
      headers = new HeadersAdaptor(request.headers());
    }
    return headers;
  }

  @Override
  public String getHeader(String headerName) {
    return headers().get(headerName);
  }

  @Override
  public synchronized MultiMap params() {
    if (params == null) {
      QueryStringDecoder queryStringDecoder = new QueryStringDecoder(uri());
      Map<String, List<String>> prms = queryStringDecoder.parameters();
      params = new CaseInsensitiveHeaders();
      if (!prms.isEmpty()) {
        for (Map.Entry<String, List<String>> entry: prms.entrySet()) {
          params.add(entry.getKey(), entry.getValue());
        }
      }
    }
    return params;
  }

  @Override
  public String getParam(String paramName) {
    return params().get(paramName);
  }

  @Override
  public synchronized HttpServerRequest handler(Handler<Buffer> dataHandler) {
    checkEnded();
    this.dataHandler = dataHandler;
    return this;
  }

  @Override
  public synchronized HttpServerRequest exceptionHandler(Handler<Throwable> handler) {
    this.exceptionHandler = handler;
    return this;
  }

  @Override
  public HttpServerRequest pause() {
    conn.pause();
    return this;
  }

  @Override
  public HttpServerRequest resume() {
    conn.resume();
    return this;
  }

  @Override
  public synchronized HttpServerRequest endHandler(Handler<Void> handler) {
    checkEnded();
    this.endHandler = handler;
    return this;
  }

  @Override
  public SocketAddress remoteAddress() {
    return conn.remoteAddress();
  }

  @Override
  public synchronized String absoluteURI() {
    if (absoluteURI == null) {
      try {
        URI uri = new URI(uri());
        String scheme = uri.getScheme();
        if (scheme != null && (scheme.equals("http") || scheme.equals("https"))) {
          absoluteURI = uri.toString();
        } else {
          absoluteURI = new URI(conn.getServerOrigin() + uri).toString();
        }
      } catch (URISyntaxException e) {
        log.error("Failed to create abs uri", e);
      }
    }
    return absoluteURI;
  }

  @Override
  public X509Certificate[] peerCertificateChain() throws SSLPeerUnverifiedException {
    return conn.getPeerCertificateChain();
  }

  @Override
  public HttpServerRequest bodyHandler(final Handler<Buffer> bodyHandler) {
    Buffer body = Buffer.buffer();
    handler(body::appendBuffer);
    endHandler(v -> bodyHandler.handle(body));
    return this;
  }

  @Override
  public synchronized NetSocket netSocket() {
    if (netSocket == null) {
      netSocket = conn.createNetSocket();
    }
    return netSocket;
  }

  @Override
  public synchronized HttpServerRequest uploadHandler(Handler<HttpServerFileUpload> handler) {
    checkEnded();
    this.uploadHandler = handler;
    return this;
  }

  @Override
  public synchronized MultiMap formAttributes() {
    if (decoder == null) {
      throw new IllegalStateException("Call expectMultiPart(true) before request body is received to receive form attributes");
    }
    return attributes();
  }

  @Override
  public String getFormAttribute(String attributeName) {
    return formAttributes().get(attributeName);
  }

  @Override
  public ServerWebSocket upgrade() {
    return conn.upgrade(this, request);
  }

  @Override
  public synchronized HttpServerRequest setExpectMultipart(boolean expect) {
    checkEnded();
    if (expect) {
      if (decoder == null) {
        String contentType = request.headers().get(HttpHeaders.Names.CONTENT_TYPE);
        if (contentType != null) {
          HttpMethod method = request.getMethod();
          String lowerCaseContentType = contentType.toLowerCase();
          isURLEncoded = lowerCaseContentType.startsWith(HttpHeaders.Values.APPLICATION_X_WWW_FORM_URLENCODED);
          if ((lowerCaseContentType.startsWith(HttpHeaders.Values.MULTIPART_FORM_DATA) || isURLEncoded) &&
            (method.equals(HttpMethod.POST) || method.equals(HttpMethod.PUT) || method.equals(HttpMethod.PATCH)
              || method.equals(HttpMethod.DELETE))) {
            decoder = new HttpPostRequestDecoder(new DataFactory(), request);
          }
        }
      }
    } else {
      decoder = null;
    }
    return this;
  }

  @Override
  public synchronized boolean isExpectMultipart() {
    return decoder != null;
  }

  @Override
  public SocketAddress localAddress() {
    return conn.localAddress();
  }

  @Override
  public synchronized boolean isEnded() {
    return ended;
  }

  synchronized void handleData(Buffer data) {
    if (decoder != null) {
      try {
        decoder.offer(new DefaultHttpContent(data.getByteBuf().duplicate()));
      } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
        handleException(e);
      }
    }
    if (dataHandler != null) {
      dataHandler.handle(data);
    }
  }

  synchronized void handleEnd() {
    ended = true;
    if (decoder != null) {
      try {
        decoder.offer(LastHttpContent.EMPTY_LAST_CONTENT);
        while (decoder.hasNext()) {
          InterfaceHttpData data = decoder.next();
          if (data instanceof Attribute) {
            Attribute attr = (Attribute) data;
            try {
              if (isURLEncoded) {
                attributes().add(urlDecode(attr.getName()), urlDecode(attr.getValue()));
              } else {
                attributes().add(attr.getName(), attr.getValue());
              }
            } catch (Exception e) {
              // Will never happen, anyway handle it somehow just in case
              handleException(e);
            }
          }
        }
      } catch (HttpPostRequestDecoder.ErrorDataDecoderException e) {
        handleException(e);
      } catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
        // ignore this as it is expected
      } finally {
        decoder.destroy();
      }
    }
    // If there have been uploads then we let the last one call the end handler once any fileuploads are complete
    if (endHandler != null) {
      endHandler.handle(null);
    }
  }

  synchronized void handleException(Throwable t) {
    if (exceptionHandler != null) {
      exceptionHandler.handle(t);
    }
  }

  private void checkEnded() {
    if (ended) {
      throw new IllegalStateException("Request has already been read");
    }
  }


  private MultiMap attributes() {
    // Create it lazily
    if (attributes == null) {
      attributes = new CaseInsensitiveHeaders();
    }
    return attributes;
  }

  private final static class NettyFileUpload implements FileUpload {

    private final HttpServerFileUploadImpl upload;
    private final String name;
    private String contentType;
    private String filename;
    private String contentTransferEncoding;
    private Charset charset;
    private boolean completed;

    private NettyFileUpload(HttpServerFileUploadImpl upload, String name, String filename, String contentType, String contentTransferEncoding, Charset charset) {
      this.upload = upload;
      this.name = name;
      this.filename = filename;
      this.contentType = contentType;
      this.contentTransferEncoding = contentTransferEncoding;
      this.charset = charset;
    }

    @Override
    public void setContent(ByteBuf channelBuffer) throws IOException {
      completed = true;
      upload.receiveData(Buffer.buffer(channelBuffer));
      upload.complete();
    }

    @Override
    public void addContent(ByteBuf channelBuffer, boolean last) throws IOException {
      upload.receiveData(Buffer.buffer(channelBuffer));
      if (last) {
        completed = true;
        upload.complete();
      }
    }

    @Override
    public void setContent(File file) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setContent(InputStream inputStream) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCompleted() {
      return completed;
    }

    @Override
    public long length() {
      throw new UnsupportedOperationException();
    }

    @Override
    public void delete() {
      throw new UnsupportedOperationException();
    }

    @Override
    public byte[] get() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public ByteBuf getChunk(int i) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getString() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getString(Charset charset) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public void setCharset(Charset charset) {
      this.charset = charset;
    }

    @Override
    public Charset getCharset() {
      return charset;
    }

    @Override
    public boolean renameTo(File file) throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInMemory() {
      return false;
    }

    @Override
    public File getFile() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public HttpDataType getHttpDataType() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int compareTo(InterfaceHttpData o) {
      return 0;
    }

    @Override
    public String getFilename() {
      return filename;
    }

    @Override
    public void setFilename(String filename) {
      this.filename = filename;
    }

    @Override
    public void setContentType(String contentType) {
      this.contentType = contentType;
    }

    @Override
    public String getContentType() {
      return contentType;
    }

    @Override
    public void setContentTransferEncoding(String contentTransferEncoding) {
      this.contentTransferEncoding = contentTransferEncoding;
    }

    @Override
    public String getContentTransferEncoding() {
      return contentTransferEncoding;
    }

    @Override
    public ByteBuf getByteBuf() throws IOException {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileUpload copy() {
      throw new UnsupportedOperationException();
    }

    //@Override
    public FileUpload duplicate() {
      throw new UnsupportedOperationException();
    }

    @Override
    public FileUpload retain() {
      return this;
    }

    @Override
    public FileUpload retain(int increment) {
      return this;
    }

    @Override
    public ByteBuf content() {
      throw new UnsupportedOperationException();
    }

    @Override
    public int refCnt() {
      return 1;
    }

    @Override
    public boolean release() {
      return false;
    }

    @Override
    public boolean release(int decrement) {
      return false;
    }
  }


  private static String urlDecode(String str) {
    return QueryStringDecoder.decodeComponent(str, CharsetUtil.UTF_8);
  }

  private class DataFactory extends DefaultHttpDataFactory {

    DataFactory() {
      super(false);
    }

    @Override
    public FileUpload createFileUpload(HttpRequest httpRequest, String name, String filename, String contentType, String contentTransferEncoding, Charset charset, long size) {
      HttpServerFileUploadImpl upload = new HttpServerFileUploadImpl(conn.vertx(), HttpServerRequestImpl.this, name, filename, contentType, contentTransferEncoding, charset,
          size);
      NettyFileUpload nettyUpload = new NettyFileUpload(upload, name, filename, contentType,
          contentTransferEncoding, charset);
      if (uploadHandler != null) {
        uploadHandler.handle(upload);
      }
      return nettyUpload;

    }
  }
}
